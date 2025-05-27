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