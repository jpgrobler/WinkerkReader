// ui/viewmodels/LidmaatDetailPastoralViewModelFactory.kt
package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import za.co.jpsoft.winkerkreader.data.pastoral.repository.FamilyMemberRepository
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase

class LidmaatDetailPastoralViewModelFactory(
    private val context: Context,
    private val memberGuid: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LidmaatDetailPastoralViewModel::class.java)) {
            // Dependencies
            val memberDao = WinkerkDatabase.getInstance(context).memberDao()
            val familyRepo = FamilyMemberRepository(memberDao)

            // ✅ Use the companion object's create() – it handles all constructor args
            val reminderRepo = PastoralReminderRepository.create(context)

            return LidmaatDetailPastoralViewModel(
                familyRepo = familyRepo,
                repository = reminderRepo,
                memberGuid = memberGuid
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}