package za.co.jpsoft.winkerkreader.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository

class LidmaatDetailPastoralViewModelFactory(
    private val context: Context,
    private val memberGuid: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LidmaatDetailPastoralViewModel::class.java)) {
            return LidmaatDetailPastoralViewModel(
                repository  = PastoralReminderRepository.create(context.applicationContext),
                memberGuid  = memberGuid
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}