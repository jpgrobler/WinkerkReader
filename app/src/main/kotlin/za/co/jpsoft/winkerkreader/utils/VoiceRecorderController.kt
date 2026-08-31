// VoiceRecorderController.kt
package za.co.jpsoft.winkerkreader.ui.utils

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.ui.views.WaveformView
import za.co.jpsoft.winkerkreader.utils.permissions.PermissionManager

/**
 * Manages the voice input UI (microphone icon, waveform, status) for a single input field.
 * Instantiate in the fragment/activity, call [setup] with the root view of the included layout.
 */
class VoiceRecorderController(
    private val fragment: Fragment,
    private val onVoiceResult: (String) -> Unit
) {

    private var textInputLayout: TextInputLayout? = null
    private var editText: TextInputEditText? = null
    private var statusContainer: View? = null
    private var tvStatus: android.widget.TextView? = null
    private var waveformView: WaveformView? = null
    private var stopButton: View? = null

    private var speechRecognizer: SpeechRecognitionManager? = null
    private var isListening = false
    private val requestCode = 1003

    /**
     * Call this after inflating the layout that includes voice_input_layout.
     * Finds all required views and sets up the microphone click listener.
     */
    fun setup(root: View) {
        textInputLayout = root.findViewById(R.id.til_voice_input)
        editText = root.findViewById(R.id.et_voice_input)
        statusContainer = root.findViewById(R.id.voice_status_container)
        tvStatus = root.findViewById(R.id.tv_voice_status)
        waveformView = root.findViewById(R.id.waveform_view)
        stopButton = root.findViewById(R.id.btn_stop_voice)

        // Create speech recognizer once
        speechRecognizer = SpeechRecognitionManager(
            context = fragment.requireContext(),
            onResult = { text ->
                appendRecognisedText(text)
                hideStatus()
            },
            onError = { error ->
                android.widget.Toast.makeText(
                    fragment.requireContext(),
                    error,
                    android.widget.Toast.LENGTH_LONG
                ).show()
                hideStatus()
            },
            onListeningStart = {
                showStatus()
                waveformView?.startRecording()
            },
            onListeningEnd = {
                // Status will be hidden when result/error arrives
            }
        ).apply {
            // Forward RMS updates to waveform
            onRmsChanged = { rms -> waveformView?.updateAmplitude(rms) }
        }

        // Set microphone click listener
        textInputLayout?.setEndIconOnClickListener {
            startListeningWithPermissionCheck()
        }

        // Stop button
        stopButton?.setOnClickListener {
            stopListening()
        }
    }

    private fun startListeningWithPermissionCheck() {
        val permissionManager = PermissionManager(fragment.requireContext())
        if (!permissionManager.isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            permissionManager.requestAudioPermissions(fragment.requireActivity())
            return
        }
        speechRecognizer?.startListening()
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        hideStatus()
    }

    private fun showStatus() {
        statusContainer?.visibility = View.VISIBLE
        tvStatus?.text = fragment.getString(R.string.nota_luister)
        textInputLayout?.isEnabled = false
        isListening = true
    }

    private fun hideStatus() {
        statusContainer?.visibility = View.GONE
        textInputLayout?.isEnabled = true
        waveformView?.stopRecording()
        isListening = false
    }

    private fun appendRecognisedText(text: String) {
        val current = editText?.text?.toString() ?: ""
        val newText = if (current.isEmpty()) text else "$current\n$text"
        editText?.setText(newText)
        editText?.setSelection(newText.length)
    }

    /**
     * Delegate permission result handling from the fragment's onRequestPermissionsResult.
     */
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == this.requestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                speechRecognizer?.startListening()
            } else {
                android.widget.Toast.makeText(
                    fragment.requireContext(),
                    "Toelating geweier - spraak-herkenning uitgeskakel",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Call in the fragment's onDestroyView to release resources.
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}