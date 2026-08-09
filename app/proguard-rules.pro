# Verset release ProGuard/R8 rules.
# isMinifyEnabled was previously false, so none of this existed before — with
# shrinking/obfuscation now on, anything reached via reflection needs an explicit
# keep rule or R8 will strip it and things will silently break at runtime instead
# of failing to compile.

# --- Kotlin / coroutines -----------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*

# --- Room ---------------------------------------------------------------------
# Entities/DAOs are referenced via generated code, not reflection, so Room itself
# needs little help — but keep the model classes anyway since they're small and
# it removes any risk of field renaming breaking column mapping.
-keep class com.johndev.verset.data.** { *; }
-dontwarn androidx.room.**

# --- Firebase Auth + Firestore --------------------------------------------------
# Firestore serializes POJOs via reflection (needs the no-arg constructor and
# original field/getter names), so any class round-tripped through Firestore
# must keep its members. com.johndev.verset.data.** above already covers our
# sync models; this keeps the SDK's own classes intact too.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# --- Credential Manager / Google Sign-In ---------------------------------------
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# --- Glance (home screen widget) ------------------------------------------------
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# --- WorkManager -----------------------------------------------------------------
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Jetpack Compose --------------------------------------------------------------
# AGP/Compose compiler already ships consumer rules for the common cases;
# this is a small safety net for stability annotations used in skipping checks.
-keep class androidx.compose.runtime.Composable
-dontwarn androidx.compose.**
