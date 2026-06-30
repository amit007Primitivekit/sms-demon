# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Room entities and DAOs
-keep class com.smsdemon.model.** { *; }
-keep interface com.smsdemon.repository.SmsLogDao { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep data classes (used with Room)
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
