package za.co.jpsoft.winkerkreader.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import za.co.jpsoft.winkerkreader.utils.prefs.WidgetPrefs

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetProviderEntryPoint {
    fun widgetPrefs(): WidgetPrefs
}