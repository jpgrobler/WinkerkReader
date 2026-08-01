package za.co.jpsoft.winkerkreader.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import za.co.jpsoft.winkerkreader.utils.CalendarManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CalendarModule {

    @Provides
    @Singleton
    fun provideCalendarManager(@ApplicationContext context: Context): CalendarManager =
        CalendarManager(context)
}