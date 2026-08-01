// di/DatabaseModule.kt
package za.co.jpsoft.winkerkreader.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import za.co.jpsoft.winkerkreader.data.calllog.CallLogDao
import za.co.jpsoft.winkerkreader.data.calllog.CallLogDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.dao.FollowUpReminderDao
import za.co.jpsoft.winkerkreader.data.room.MemberDao
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePastoralDatabase(@ApplicationContext context: Context): PastoralDatabase =
        PastoralDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideFollowUpReminderDao(database: PastoralDatabase): FollowUpReminderDao =
        database.followUpReminderDao()

    @Provides
    @Singleton
    fun provideMemberDao(@ApplicationContext context: Context): MemberDao =
        WinkerkDatabase.getInstance(context).memberDao()

    @Provides
    @Singleton
    fun provideCallLogDao(@ApplicationContext context: Context): CallLogDao =
        CallLogDatabase.getInstance(context).callLogDao()

    // NEW: Provide WinkerkDatabase
    @Provides
    @Singleton
    fun provideWinkerkDatabase(@ApplicationContext context: Context): WinkerkDatabase =
        WinkerkDatabase.getInstance(context)
}