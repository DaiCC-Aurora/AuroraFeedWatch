# ExoPlayer / Media3
-keep class androidx.media3.** { *; }

# Retrofit / Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.aurora.podcast.data.network.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**