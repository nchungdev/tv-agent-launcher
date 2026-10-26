# Shrink and optimise:
-repackageclasses 'com.gathin.tvhome.a'
-allowaccessmodification
-optimizationpasses 5
-overloadaggressively

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
