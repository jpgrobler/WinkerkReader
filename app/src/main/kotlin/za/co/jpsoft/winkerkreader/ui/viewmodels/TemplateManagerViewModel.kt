package za.co.jpsoft.winkerkreader.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.data.pastoral.model.TemplateWithSteps
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository

class TemplateManagerViewModel(
    private val repository: PastoralReminderRepository
) : ViewModel() {

    val templates: StateFlow<List<TemplateWithSteps>> =
        repository.observeAllTemplates()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    private val _templateCreated = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Emits the new templateId so the Activity can immediately open the editor. */
    val templateCreated: SharedFlow<String> = _templateCreated.asSharedFlow()

    fun createTemplate(titleAf: String, descriptionAf: String?) {
        viewModelScope.launch {
            try {
                val id = repository.createTemplate(titleAf, descriptionAf)
                _templateCreated.tryEmit(id)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie sjabloon skep nie: ${e.message}")
            }
        }
    }

    fun setActive(templateId: String, isActive: Boolean) {
        viewModelScope.launch {
            try {
                repository.setTemplateActive(templateId, isActive)
            } catch (e: Exception) {
                _error.tryEmit("Kon nie sjabloon opdateer nie")
            }
        }
    }

    fun deletePermanently(templateId: String) {
        viewModelScope.launch {
            try {
                repository.deleteTemplatePermanently(templateId)
            } catch (e: IllegalStateException) {
                _error.tryEmit("Stelsel-sjablone kan nie permanent verwyder word nie")
            } catch (e: Exception) {
                _error.tryEmit("Kon nie sjabloon verwyder nie")
            }
        }
    }

    class Factory(private val repository: PastoralReminderRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TemplateManagerViewModel::class.java)) {
                return TemplateManagerViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}