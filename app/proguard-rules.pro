# IPTV Family ProGuard Rules

# Keep Parcelable implementations for Hilt
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Room entities and DAOs
-keep class com.iptv.family.data.local.** { *; }

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep class * extends dagger.hilt.android.HiltApplication { *; }

# Keep ExoPlayer
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# Keep Retrofit and Gson
-keep class com.squareup.retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }

# Keep Coil
-keep class coil.** { *; }

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Media3 Session
-keep class androidx.media3.session.** { *; }

# Keep Room
-keep class androidx.room.** { *; }

# Keep Serialization
-keep class kotlinx.serialization.** { *; }

# Suppress warnings
-dontwarn com.squareup.okhttp3.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.media3.**

# Keep DataStore
-keep class androidx.datastore.** { *; }