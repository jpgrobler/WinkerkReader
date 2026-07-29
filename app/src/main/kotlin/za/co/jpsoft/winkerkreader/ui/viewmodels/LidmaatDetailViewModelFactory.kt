// ui/viewmodels/LidmaatDetailViewModelFactory.kt
package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import za.co.jpsoft.winkerkreader.data.pastoral.repository.FamilyMemberRepository
import za.co.jpsoft.winkerkreader.data.room.WinkerkDatabase

class LidmaatDetailViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LidmaatDetailViewModel::class.java)) {
            val memberDao = WinkerkDatabase.getInstance(application).memberDao()
            val familyRepo = FamilyMemberRepository(memberDao)
            return LidmaatDetailViewModel(application, familyRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}