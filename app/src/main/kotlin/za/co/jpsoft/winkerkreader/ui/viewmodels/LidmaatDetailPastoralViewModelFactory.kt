// ui/viewmodels/LidmaatDetailPastoralViewModelFactory.kt
package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import za.co.jpsoft.winkerkreader.data.pastoral.repository.FamilyMemberRepository
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase

@Singleton
class LidmaatDetailPastoralViewModelFactory @Inject constructor(
    private val familyRepo: FamilyMemberRepository,
    private val repository: PastoralReminderRepository
) {
    fun create(memberGuid: String): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(LidmaatDetailPastoralViewModel::class.java)) {
                    return LidmaatDetailPastoralViewModel(familyRepo, repository, memberGuid) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}