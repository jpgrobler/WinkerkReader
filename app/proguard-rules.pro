# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\Pieter Grobler\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

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

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString

# ============================================================
# Room — entities, DAOs, database, relations
# ============================================================

# Keep all Room entity classes (annotated with @Entity)
-keep @androidx.room.Entity class * { *; }

# Keep all Room DAO interfaces (annotated with @Dao)
-keep @androidx.room.Dao class * { *; }

# Keep the Room database class
-keep class za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase { *; }

# Keep pastoral entity classes explicitly (belt-and-braces)
-keep class za.co.jpsoft.winkerkreader.data.pastoral.entities.** { *; }

# Keep pastoral DAO interfaces
-keep class za.co.jpsoft.winkerkreader.data.pastoral.dao.** { *; }

# Keep pastoral model classes used in @Relation and @Embedded
-keep class za.co.jpsoft.winkerkreader.data.pastoral.model.** { *; }

# Keep PastoralMetaEntity — single-row config table; field names must survive
-keepclassmembers class za.co.jpsoft.winkerkreader.data.pastoral.entities.PastoralMetaEntity {
    <fields>;
}

# Room uses reflection to read/write @ColumnInfo fields;
# prevent shrinking of any field used as a Room column
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract !static <methods>;
}

# Keep generated Room _Impl classes (generated at compile time)
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }

# ============================================================
# Coroutines — required for CoroutineWorker and suspend DAOs
# ============================================================
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================================
# WorkManager — FollowUpReminderWorker must survive shrinking
# ============================================================
-keep class za.co.jpsoft.winkerkreader.workers.FollowUpReminderWorker { *; }

# ============================================================
# BroadcastReceiver — action receiver must survive shrinking
# ============================================================
-keep class za.co.jpsoft.winkerkreader.receivers.PastoralReminderActionReceiver { *; }

# ============================================================
# Enum classes — ReminderStatus, ScheduleType stored as strings
# ============================================================
-keepclassmembers enum za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderStatus {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}