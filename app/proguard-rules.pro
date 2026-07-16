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

# ============================================================
# PopupMenu Reflection (CRITICAL - prevents crash)
# ============================================================
-keepclassmembers class androidx.appcompat.widget.PopupMenu { *; }

# ============================================================
# SQLiteStatementValidator
# ============================================================
-keepclassmembers class za.co.jpsoft.winkerkreader.utils.SQLiteStatementValidator { *; }

# ============================================================
# Room
# ============================================================
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase { *; }
-keep class za.co.jpsoft.winkerkreader.data.pastoral.entities.** { *; }
-keep class za.co.jpsoft.winkerkreader.data.pastoral.dao.** { *; }
-keep class za.co.jpsoft.winkerkreader.data.pastoral.model.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract !static <methods>;
}
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }

# ============================================================
# Glide (required for photo loading)
# ============================================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$** {
  <init>(...);
}

# ============================================================
# Parcelable
# ============================================================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ============================================================
# Gson (if used)
# ============================================================
-keep class za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox { *; }
-keep class za.co.jpsoft.winkerkreader.data.models.FilterBox { *; }

# ============================================================
# AndroidX Lifecycle / ViewModel
# ============================================================
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# ============================================================
# Kotlin / Coroutines
# ============================================================
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public *;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================================
# WorkManager
# ============================================================
-keep class za.co.jpsoft.winkerkreader.workers.FollowUpReminderWorker { *; }

# ============================================================
# BroadcastReceiver
# ============================================================
-keep class za.co.jpsoft.winkerkreader.receivers.PastoralReminderActionReceiver { *; }

# ============================================================
# Enums (stored as strings in DB)
# ============================================================
-keepclassmembers enum za.co.jpsoft.winkerkreader.data.pastoral.model.ReminderStatus {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum za.co.jpsoft.winkerkreader.data.pastoral.model.ScheduleType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# Suppress warnings
# ============================================================
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString