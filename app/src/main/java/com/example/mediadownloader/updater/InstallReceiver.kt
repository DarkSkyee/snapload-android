package com.example.mediadownloader.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import java.io.File

class InstallReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_STATUS = "com.example.mediadownloader.ACTION_INSTALL_STATUS"
        const val EXTRA_APK_PATH = "com.example.mediadownloader.extra.APK_PATH"
        private const val TAG = "InstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) {
            return
        }

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val apkPath = intent.getStringExtra(EXTRA_APK_PATH)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.d(TAG, "Package installer requested user confirmation.")
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmationIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(it)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch installer confirmation activity: ${e.message}", e)
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Package installed successfully!")
                // Automatically remove downloaded APK immediately upon successful install
                if (!apkPath.isNullOrBlank()) {
                    try {
                        val apkFile = File(apkPath)
                        if (apkFile.exists()) {
                            apkFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete installed APK: ${e.message}")
                    }
                }
                AppUpdateManager.cleanupUpdatesDir(context)
            }

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Log.e(TAG, "Package installation failed: status=$status, message=$message")
                // Cleanup invalid APK files to prevent Play Protect scanning alerts
                if (status == PackageInstaller.STATUS_FAILURE_INVALID ||
                    status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
                ) {
                    if (!apkPath.isNullOrBlank()) {
                        try {
                            File(apkPath).delete()
                        } catch (_: Exception) {}
                    }
                }
            }

            else -> {
                Log.d(TAG, "Package installation status update: status=$status, message=$message")
            }
        }
    }
}
