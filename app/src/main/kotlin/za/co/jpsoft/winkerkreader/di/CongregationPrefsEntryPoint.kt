package za.co.jpsoft.winkerkreader.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import za.co.jpsoft.winkerkreader.utils.prefs.CongregationPrefs

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CongregationPrefsEntryPoint {
    fun congregationPrefs(): CongregationPrefs
}