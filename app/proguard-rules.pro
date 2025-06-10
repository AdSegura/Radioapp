# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep main entry points
-keep class com.google.gson.** { *; }
-keep class org.json.** { *; }
-keep class kotlinx.serialization.** { *; }

# Keep your data models
-keep class com.example.radiostreamingapp.** { *; }

# Keep reflection for JSON parsing
-keepattributes *Annotation*
-keepattributes Signature
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep public class * extends android.app.Application
-keep public class * extends androidx.activity.ComponentActivity

-dontwarn javax.inject.**
-dontwarn dagger.hilt.**

# Logging - Keep errors but remove debug logs
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
}
# Keep error logs
-keep class android.util.Log {
    public static *** e(...);
}

# ==============================================
# MEDIA3 EXOPLAYER RULES
# ==============================================

# Keep Media3 core classes
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.extractor.** { *; }

# Media3 annotations and unstable API
-dontwarn androidx.media3.common.util.UnstableApi
-keep @androidx.media3.common.util.UnstableApi class * { *; }

# Media session compatibility
-keep class androidx.media.** { *; }
-keep class android.support.v4.media.** { *; }

# ==============================================
# JETPACK COMPOSE SPECIFIC RULES
# ==============================================

# Keep Compose compiler generated classes
-keep class androidx.compose.compiler.** { *; }

# Prevent optimization of Compose State classes
-keep class androidx.compose.runtime.State { *; }
-keep class androidx.compose.runtime.MutableState { *; }
-keep class androidx.compose.runtime.snapshots.SnapshotStateList { *; }
-keep class androidx.compose.runtime.snapshots.SnapshotStateMap { *; }

# Keep Compose UI classes
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.foundation.** { *; }

# Prevent obfuscation of Compose function names
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep lambda expressions used in Compose
-keepclassmembers class * {
    kotlin.jvm.functions.Function* *;
}

# Keep Compose runtime snapshots
-keep,allowobfuscation,allowshrinking class androidx.compose.runtime.snapshots.SnapshotStateList
-keepclassmembers class androidx.compose.runtime.snapshots.SnapshotStateList {
    boolean conditionalUpdate(boolean, kotlin.jvm.functions.Function1);
    *** conditionalUpdate(...);
}

# Keep all methods that might have lock verification issues
-keepclassmembers class androidx.compose.runtime.snapshots.** {
    *** conditionalUpdate(...);
    *** *(...);
}

# Additional safety for API 31+ and Compose
-dontwarn androidx.compose.**
-dontwarn kotlin.reflect.jvm.internal.**

# ==============================================
# KOTLIN COROUTINES
# ==============================================

# Keep StateFlow and Flow classes
-keep class kotlinx.coroutines.flow.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Keep ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ==============================================
# DEPENDENCY INJECTION (HILT)
# ==============================================

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ==============================================
# ANDROID COMPONENTS
# ==============================================

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Service classes
-keep class * extends android.app.Service { *; }

# Keep BroadcastReceiver classes
-keep class * extends android.content.BroadcastReceiver { *; }

# ==============================================
# R8 COMPATIBILITY
# ==============================================

# R8 full mode compatibility
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations

# Keep generic signatures for proper type erasure handling
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ==============================================
# OPTIMIZATION SETTINGS
# ==============================================

# Conservative optimization to avoid issues
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 2

# Don't warn about missing classes that are not used
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement