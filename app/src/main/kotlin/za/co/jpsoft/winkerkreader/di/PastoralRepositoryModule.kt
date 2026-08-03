package za.co.jpsoft.winkerkreader.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import za.co.jpsoft.winkerkreader.data.pastoral.repository.ReminderBackupHelper
import za.co.jpsoft.winkerkreader.utils.db.PastoralDatabaseBackup
import za.co.jpsoft.winkerkreader.utils.prefs.PastoralPrefs
import za.co.jpsoft.winkerkreader.utils.prefs.TasksPrefs

@Module
@InstallIn(SingletonComponent::class)
object PastoralRepositoryModule {

    @Provides
    @Singleton
    fun provideReminderBackupHelper(
        @ApplicationContext context: Context,
        pastoralDbBackup: PastoralDatabaseBackup
    ): ReminderBackupHelper {
        return ReminderBackupHelper(context, pastoralDbBackup)
    }

    @Provides
    @Singleton
    fun providePastoralReminderRepository(
        @ApplicationContext context: Context,
        pastoralPrefs: PastoralPrefs,
        tasksPrefs: TasksPrefs,
        backupHelper: ReminderBackupHelper
    ): PastoralReminderRepository {
        return PastoralReminderRepository.create(context, pastoralPrefs, tasksPrefs, backupHelper)
    }
}