# ProGuard / R8 rules for SnapLoad - Optimized for Google Play Protect & stability

# Retain clean stack traces, annotations, and metadata (prevents ML malware heuristics)
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep auto-updater models, JSON responses, and PackageInstaller BroadcastReceiver
-keep class com.example.mediadownloader.updater.ReleaseInfo { *; }
-keep class com.example.mediadownloader.updater.UpdateCheckResult** { *; }
-keep class com.example.mediadownloader.updater.InstallReceiver { *; }
-keep class com.example.mediadownloader.updater.AppUpdateManager** { *; }
-keep class com.example.mediadownloader.model.** { *; }
-keepclassmembers class com.example.mediadownloader.model.** { *; }

# Preserve app components to guarantee zero startup/runtime reflection issues
-keep class com.example.mediadownloader.** { *; }
-keepclassmembers class com.example.mediadownloader.** { *; }

# Preserve youtubedl-android core library and ffmpeg classes, JNI, and reflection bindings
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Jackson JSON processor reflection rules
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Apache Commons IO & Compress
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# KotlinX Serialization
-dontwarn kotlinx.serialization.**
