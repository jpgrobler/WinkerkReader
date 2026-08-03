// di/PreferencesModule.kt
package za.co.jpsoft.winkerkreader.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.utils.prefs.AppearancePrefs
import za.co.jpsoft.winkerkreader.utils.prefs.BackupPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.BirthdaySmsPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CalendarPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.MemberListPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.PastoralPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.QuickActionPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.SecurityPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.TasksPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.WidgetPrefs
import za.co.jpsoft.winkerkreader.utils.security.EncryptedPrefsManager
import javax.inject.Qualifier

// ─── Qualifiers ──────────────────────────────────────────────────────────────

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UserPrefs

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SecurePrefs

// ─── Module ──────────────────────────────────────────────────────────────────

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    // ─── SharedPreferences providers ────────────────────────────────────────

    @Provides
    @Singleton
    @UserPrefs
    fun provideUserPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    @SecurePrefs
    fun provideSecurePrefs(@ApplicationContext context: Context): SharedPreferences =
        EncryptedPrefsManager.getSecurePrefs(context)

    // ─── *Prefs providers ─────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideWidgetPrefs(@UserPrefs prefs: SharedPreferences): WidgetPrefs =
        WidgetPrefs(prefs)

    @Provides
    @Singleton
    fun provideQuickActionPrefs(@UserPrefs prefs: SharedPreferences): QuickActionPrefs =
        QuickActionPrefs(prefs)

    @Provides
    @Singleton
    fun provideBackupPrefs(@UserPrefs prefs: SharedPreferences): BackupPrefs =
        BackupPrefs(prefs)

    @Provides
    @Singleton
    fun providePastoralPrefs(@UserPrefs prefs: SharedPreferences): PastoralPrefs =
        PastoralPrefs(prefs)

    @Provides
    @Singleton
    fun provideCallMonitorPrefs(@UserPrefs prefs: SharedPreferences): CallMonitorPrefs =
        CallMonitorPrefs(prefs)

    @Provides
    @Singleton
    fun provideBirthdaySmsPrefs(@UserPrefs prefs: SharedPreferences): BirthdaySmsPrefs =
        BirthdaySmsPrefs(prefs)

    @Provides
    @Singleton
    fun provideSyncPrefs(@UserPrefs prefs: SharedPreferences): SyncPrefs =
        SyncPrefs(prefs)

    @Provides
    @Singleton
    fun provideMemberListPrefs(@UserPrefs prefs: SharedPreferences): MemberListPrefs =
        MemberListPrefs(prefs)

    @Provides
    @Singleton
    fun provideAppearancePrefs(@UserPrefs prefs: SharedPreferences): AppearancePrefs =
        AppearancePrefs(prefs)

    @Provides
    @Singleton
    fun provideCongregationPrefs(
        @UserPrefs prefs: SharedPreferences,
        @ApplicationContext context: Context
    ): CongregationPrefs =
        CongregationPrefs(prefs, context)

    // SecurityPrefs needs both regular and encrypted prefs
    @Provides
    @Singleton
    fun provideSecurityPrefs(
        @UserPrefs prefs: SharedPreferences,
        @SecurePrefs securePrefs: SharedPreferences
    ): SecurityPrefs =
        SecurityPrefs(prefs, securePrefs)

    // TasksPrefs also needs both
    @Provides
    @Singleton
    fun provideTasksPrefs(
        @UserPrefs prefs: SharedPreferences,
        @SecurePrefs securePrefs: SharedPreferences
    ): TasksPrefs =
        TasksPrefs(prefs, securePrefs)

    // ─── CalendarPrefs ────────────────────────────────────────────────────
    @Provides
    @Singleton
    @UserPrefs   // <-- MUST add this qualifier to match injection sites
    fun provideCalendarPrefs(@UserPrefs prefs: SharedPreferences): CalendarPrefs =
        CalendarPrefs(prefs)
}