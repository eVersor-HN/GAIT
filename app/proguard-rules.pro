# GAIT release shrinking rules. Room, Compose, WorkManager, and play-services ship their own
# consumer rules; what's below covers only this app's own reflection and serialization.

# GaitViewModelFactory instantiates ViewModels reflectively by constructor signature
# (GaitRepository[, Context]). Keep every ViewModel's constructors.
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Room entities are reflected on by the generated DAOs; keep their shape intact.
-keep class dev.eversorhn.gait.data.db.entity.** { *; }

# Enum names are persisted (TrackingMode in ActiveSessionStore) -- don't let R8 rename them.
-keepclassmembers enum dev.eversorhn.gait.tracking.TrackingMode { *; }

# Keep line numbers for readable crash reports from release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
