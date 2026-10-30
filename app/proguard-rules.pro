# ProGuard rules for SnapLoad

# Preserve all app classes completely to guarantee zero startup/runtime reflection issues
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

# KotlinX Serialization and debugging attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn kotlinx.serialization.**
