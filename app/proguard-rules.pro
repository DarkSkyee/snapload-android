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

# KotlinX Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlinx.serialization.**
