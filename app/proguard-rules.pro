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
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ==============================================
# JETPACK COMPOSE SPECIFIC RULES
# ==============================================

# ALTERNATIVE SOLUTION: More aggressive Compose protection
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

# Disable ALL optimizations to prevent lock verification issues
#-dontoptimize
#-dontobfuscate

# Alternative: If you need some optimization, use conservative settings
# -optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable
# -optimizationpasses 1

# Keep ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep StateFlow and Flow classes
-keep class kotlinx.coroutines.flow.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Additional safety for API 31+ and Compose
-dontwarn androidx.compose.**
-dontwarn kotlin.reflect.jvm.internal.**

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# R8 full mode compatibility
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations

# Keep generic signatures for proper type erasure handling
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod