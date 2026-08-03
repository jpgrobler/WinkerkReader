package za.co.jpsoft.winkerkreader.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import za.co.jpsoft.winkerkreader.data.calllog.dao.CallLogDao
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import za.co.jpsoft.winkerkreader.utils.prefs.CallMonitorPrefs
import za.co.jpsoft.winkerkreader.utils.telephony.UnifiedCallMonitor

@Module
@InstallIn(SingletonComponent::class)
object CallMonitorModule {

    @Provides
    @Singleton
    fun provideUnifiedCallMonitor(
        @ApplicationContext context: Context,
        callLogDao: CallLogDao,
        calendarManager: CalendarManager,
        callMonitorPrefs: CallMonitorPrefs
    ): UnifiedCallMonitor {
        return UnifiedCallMonitor(context, callLogDao, calendarManager, callMonitorPrefs)
    }
}