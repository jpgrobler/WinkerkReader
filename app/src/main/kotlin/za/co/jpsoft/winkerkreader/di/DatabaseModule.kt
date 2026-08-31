// di/DatabaseModule.kt
package za.co.jpsoft.winkerkreader.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import za.co.jpsoft.winkerkreader.data.calllog.dao.CallLogDao
import za.co.jpsoft.winkerkreader.data.calllog.setup.CallLogDatabase
import za.co.jpsoft.winkerkreader.data.members.dao.ArgiefDao
import za.co.jpsoft.winkerkreader.data.members.dao.LiveMemberDao
import za.co.jpsoft.winkerkreader.data.members.dao.MemberDao
import za.co.jpsoft.winkerkreader.data.members.setup.WinkerkDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.dao.FollowUpReminderDao
import za.co.jpsoft.winkerkreader.data.pastoral.dao.PastoralNoteDao
import za.co.jpsoft.winkerkreader.data.pastoral.dao.ReminderTemplateDao
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralNoteRepository

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideArgiefDao(database: WinkerkDatabase): ArgiefDao = database.argiefDao()

    @Provides
    @Singleton
    fun providePastoralNoteDao(database: PastoralDatabase): PastoralNoteDao =
        database.pastoralNoteDao()

    @Provides
    @Singleton
    fun provideReminderTemplateDao(database: PastoralDatabase): ReminderTemplateDao =
        database.reminderTemplateDao()

    @Provides
    @Singleton
    fun providePastoralDatabase(@ApplicationContext context: Context): PastoralDatabase =
        PastoralDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideFollowUpReminderDao(database: PastoralDatabase): FollowUpReminderDao =
        database.followUpReminderDao()

    /**
     * Always resolves to the live companion DB. A plain Room DAO snapshot becomes
     * invalid after [WinkerkDatabase.closeInstance] during import/swap.
     */
    @Provides
    @Singleton
    fun provideMemberDao(@ApplicationContext context: Context): MemberDao =
        LiveMemberDao(context.applicationContext)

    @Provides
    @Singleton
    fun provideCallLogDao(@ApplicationContext context: Context): CallLogDao =
        CallLogDatabase.getInstance(context).callLogDao()

    /**
     * Prefer [WinkerkDatabase.getInstance] at call sites that survive a DB swap.
     * This binding is for constructors that need a Room type; it may become stale
     * after closeInstance — use getInstance() for long-lived work.
     */
    @Provides
    fun provideWinkerkDatabase(@ApplicationContext context: Context): WinkerkDatabase =
        WinkerkDatabase.getInstance(context)

    @Provides
    @Singleton
    fun providePastoralNoteRepository(@ApplicationContext context: Context): PastoralNoteRepository =
        PastoralNoteRepository(context)
}