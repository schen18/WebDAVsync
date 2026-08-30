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

# ---------------------------------------------------------------------------
# com.dissonance.webdav
# Minification is currently disabled (isMinifyEnabled = false); the rules
# below keep the app's entry points safe if it is ever enabled.
# ---------------------------------------------------------------------------

# Application, launcher activity, and providers are referenced by fully
# qualified class names from AndroidManifest.xml and must keep their names.
-keep class com.dissonance.webdav.WebDavSyncApp { *; }
-keep class com.dissonance.webdav.MainActivity { *; }
-keep class com.dissonance.webdav.provider.SyncDocumentsProvider { *; }
-keep class com.dissonance.webdav.provider.SyncHubProvider { *; }

# IPC contract for companion client apps: the provider authorities and
# signature permission strings must remain stable across releases.
-keep class com.dissonance.webdav.provider.SyncHubContract {
    public static final java.lang.String *;
    public static * android.net.Uri;
}

# WorkManager instantiates ListenableWorker subclasses reflectively from
# class names persisted in WorkSpec (androidx.work ships default keep rules
# for the constructor signature; keep the classes themselves as well).
-keep class com.dissonance.webdav.sync.work.** extends androidx.work.ListenableWorker {
    public <init>(...);
}
