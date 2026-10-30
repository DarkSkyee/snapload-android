# Proguard / R8 Configuration for SnapLoad

# Preserve yt-dlp native and python bridge classes
-keep class com.yausername.youtubedl_android.** { *; }
-dontwarn com.yausername.youtubedl_android.**
-keep class com.yausername.ffmpeg.** { *; }
-dontwarn com.yausername.ffmpeg.**

# Keep models for serialization
-keep class com.example.mediadownloader.model.** { *; }

# Coil image loader
-keep class coil.** { *; }
-dontwarn coil.**

# Jackson JSON processor reflection rules
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Apache Commons IO & Compress
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# KotlinX Serialization and debugging attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn kotlinx.serialization.**
