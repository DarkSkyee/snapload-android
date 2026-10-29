# ProGuard rules for SnapLoad

# Preserve youtubedl-android core library classes, JNI, and reflection bindings
-keep class com.yausername.youtubedl_android.** { *; }
-dontwarn com.yausername.youtubedl_android.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# KotlinX Serialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlinx.serialization.**
