# Shrink and optimise:
-repackageclasses 'a'
-allowaccessmodification
-optimizationpasses 5
-overloadaggressively
-renamesourcefileattribute ''

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(...);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static *;
}

-assumenosideeffects class kotlinx.coroutines.internal.StackTraceRecoveryKt {
    static *;
}

-assumenosideeffects class kotlinx.coroutines.DebugStringsKt {
    static *;
}
