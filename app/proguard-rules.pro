# FreeFCC Custom ProGuard / R8 Optimization Rules

# Preserve data classes and serialization models
-keepclassmembers class com.freefcc.app.** {
    *** get*();
    *** set*(...);
}
-keep class com.freefcc.app.AppState { *; }
-keep class com.freefcc.app.UpdateInfo { *; }
-keep class com.freefcc.app.DumlFrame { *; }
-keep class com.freefcc.app.AircraftModelIdentity { *; }

# Strip debug and verbose logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}