package za.co.jpsoft.winkerkreader.ui.utils

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputLayout
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.ui.views.WaveformView
import za.co.jpsoft.winkerkreader.utils.permissions.PermissionManager

/**
 * Reusable helper for attaching speech‑to‑text to a TextInputLayout,
 * with permission handling, status UI, and waveform visualisation.
 *
 * @param fragment The hosting fragment (for permission requests and lifecycle).
 * @param tilField The TextInputLayout containing the note EditText.
 * @param voiceStatusContainer The container holding the status UI (shows/hides).
 * @param tvStatus The TextView inside the status container for status text.
 * @param waveformView The WaveformView to animate during recording.
 * @param stopButton The stop button (optional; if null, the status container must have one with id btn_stop_voice).
 * @param onVoiceResult Callback invoked when speech is recognised.
 * @param requestCode Permission request code (default 1003).
 */
class VoiceNoteHelper(
    private val fragment: Fragment,
    private val tilField: TextInputLayout,
    private val voiceStatusContainer: View,
    private val tvStatus: TextView,
    private val waveformView: WaveformView,
    private val stopButton: View? = null,
    private val requestCode: Int = PERMISSION_REQUEST_VOICE,
    private val onVoiceResult: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognitionManager? = null
    private var isListening = false

    init {
        setupVoiceIcon()
        setupStopButton()
    }

    private fun setupVoiceIcon() {
        speechRecognizer = SpeechRecognitionManager(
            context = fragment.requireContext(),
            onResult = { text ->
                onVoiceResult(text)
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
                waveformView.startRecording()
            },
            onListeningEnd = {
                // Status will be hidden when result/error arrives
            }
        )

        // Connect RMS updates to waveform
        speechRecognizer?.onRmsChanged = { rms ->
            waveformView.updateAmplitude(rms)
        }

        tilField.setEndIconOnClickListener {
            startListeningWithPermissionCheck()
        }
    }

    private fun setupStopButton() {
        val btn = stopButton ?: voiceStatusContainer.findViewById<View>(R.id.btn_stop_voice)
        btn?.setOnClickListener {
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

    fun stopListening() {
        speechRecognizer?.stopListening()
        hideStatus()
    }

    private fun showStatus() {
        voiceStatusContainer.visibility = View.VISIBLE
        tvStatus.text = fragment.getString(R.string.nota_luister)
        tilField.isEnabled = false
        isListening = true
    }

    private fun hideStatus() {
        voiceStatusContainer.visibility = View.GONE
        tilField.isEnabled = true
        waveformView.stopRecording()
        isListening = false
    }

    /**
     * Call from the fragment's onRequestPermissionsResult.
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
     * Call from the fragment's onDestroyView to release resources.
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    companion object {
        const val PERMISSION_REQUEST_VOICE = 1003
    }
}