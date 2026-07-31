package za.co.jpsoft.winkerkreader.ui.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import za.co.jpsoft.winkerkreader.databinding.FragmentUitlegWidgetBinding
import za.co.jpsoft.winkerkreader.utils.SettingsManager

class UitlegWidgetFragment : Fragment() {

    private var _binding: FragmentUitlegWidgetBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager

    private var initialDoop = false
    private var initialBelydenis = false
    private var initialHuwelik = false
    private var initialSterf = false

    private var isInitializing = true
    private var isDirty = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUitlegWidgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager.getInstance(requireContext())

        loadPreferences()
        setupListeners()
        isInitializing = false
        isDirty = false

        Handler(Looper.getMainLooper()).postDelayed({
            updateSaveButtonState()
        }, 300)
    }

    private fun loadPreferences() {
        initialDoop = settingsManager.widget.widgetDoop
        binding.widgetDoopSelect.isChecked = initialDoop

        initialBelydenis = settingsManager.widget.widgetBelydenis
        binding.widgetBelydenisSelect.isChecked = initialBelydenis

        initialHuwelik = settingsManager.widget.widgetHuwelik
        binding.widgetHuwelikSelect.isChecked = initialHuwelik

        initialSterf = settingsManager.widget.widgetSterf
        binding.widgetSterf.isChecked = initialSterf   // fixed: removed .widget
    }

    private fun setupListeners() {
        val checkboxes = listOf(
            binding.widgetDoopSelect,
            binding.widgetBelydenisSelect,
            binding.widgetHuwelikSelect,
            binding.widgetSterf                     // fixed: removed .widget
        )
        checkboxes.forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ -> onUserChanged() }
        }

        binding.saveWidget.setOnClickListener { saveWidgetSettings() }
    }

    private fun onUserChanged() {
        if (!isInitializing) {
            isDirty = true
            updateSaveButtonState()
        }
    }

    private fun updateSaveButtonState() {
        val enabled = isDirty || isAnySettingChanged()
        binding.saveWidget.isEnabled = enabled
        binding.saveWidget.alpha = if (enabled) 1.0f else 0.4f
    }

    private fun isAnySettingChanged(): Boolean {
        if (binding.widgetDoopSelect.isChecked != initialDoop) return true
        if (binding.widgetBelydenisSelect.isChecked != initialBelydenis) return true
        if (binding.widgetHuwelikSelect.isChecked != initialHuwelik) return true
        if (binding.widgetSterf.isChecked != initialSterf) return true   // fixed
        return false
    }

    private fun saveWidgetSettings() {
        settingsManager.widget.widgetDoop = binding.widgetDoopSelect.isChecked
        settingsManager.widget.widgetBelydenis = binding.widgetBelydenisSelect.isChecked
        settingsManager.widget.widgetHuwelik = binding.widgetHuwelikSelect.isChecked
        settingsManager.widget.widgetSterf = binding.widgetSterf.isChecked   // fixed

        initialDoop = binding.widgetDoopSelect.isChecked
        initialBelydenis = binding.widgetBelydenisSelect.isChecked
        initialHuwelik = binding.widgetHuwelikSelect.isChecked
        initialSterf = binding.widgetSterf.isChecked   // fixed

        isDirty = false
        updateSaveButtonState()
        Toast.makeText(requireContext(), "Widget-instellings gestoor", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}