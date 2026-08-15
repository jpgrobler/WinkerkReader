package za.co.jpsoft.winkerkreader.ui.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import java.util.Locale

/**
 * Manages speech-to-text recognition for pastoral notes.
 *
 * Handles Afrikaans language recognition via device's speech recognition service.
 * Provides callbacks for partial results and errors during dictation.
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStart: () -> Unit = {},
    private val onListeningEnd: () -> Unit = {}
) : RecognitionListener {
    var onRmsChanged: ((Float) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    init {
        // Check if speech recognition is available
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Spraak-herkenning is nie op hierdie toestel beskikbaar nie")
        }
    }

    /**
     * Start listening for speech input in Afrikaans
     */
    fun startListening() {
        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(this)
            }

            val langCode = getLanguageCode(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)

                // Alternative: Use system default if af-ZA not available
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "af"
                )

                // Show speech recognizer UI
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_PROMPT,
                    "Praat nou..."
                )

                // Get partial results as user speaks
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                // Request online recognition for better accuracy
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE,
                    false
                )

                // Max results
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
                )
            }

            isListening = true
            onListeningStart()
            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("SpeechRecognition", "Error starting listening", e)
            onError("Kon nie spraak-herkenning begin nie: ${e.message}")
            isListening = false
        }
    }

    /**
     * Stop listening for speech input
     */
    fun stopListening() {
        try {
            isListening = false
            speechRecognizer?.stopListening()
            onListeningEnd()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("SpeechRecognition", "Error stopping listening", e)
        }
    }

    /**
     * Cancel ongoing speech recognition
     */
    fun cancel() {
        try {
            isListening = false
            speechRecognizer?.cancel()
            onListeningEnd()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("SpeechRecognition", "Error canceling", e)
        }
    }

    /**
     * Release resources
     */
    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("SpeechRecognition", "Error destroying", e)
        }
    }

    // ── RecognitionListener callbacks ──────────────────────────────────────

    override fun onReadyForSpeech(params: android.os.Bundle?) {
        if (BuildConfig.DEBUG) Log.d("SpeechRecognition", "Ready for speech")
    }

    override fun onBeginningOfSpeech() {
        if (BuildConfig.DEBUG) Log.d("SpeechRecognition", "Speech started")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // 👇 NEW: forward RMS to the callback
        onRmsChanged?.invoke(rmsdB)
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Audio buffer received
    }

    override fun onEndOfSpeech() {
        if (BuildConfig.DEBUG) Log.d("SpeechRecognition", "Speech ended")
    }

    override fun onError(error: Int) {
        isListening = false
        onListeningEnd()

        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Oudioprobleem - kan nie opname maak nie"
            SpeechRecognizer.ERROR_CLIENT -> "Kliëntfout - probeer weer"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissies nie verleen nie - gee geluid-toelating"
            SpeechRecognizer.ERROR_NETWORK -> "Netwerkfout - kontroleer konneksie"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netwerkfouttyd - probeer weer"
            SpeechRecognizer.ERROR_NO_MATCH -> "Kon nie spraak verstaan nie - probeer weer"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Herkenner is besig - probeer later"
            SpeechRecognizer.ERROR_SERVER -> "Bediener-fout - probeer later"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Geen spraak waargeneem - probeer weer"
            else -> "Onbekende fout: $error"
        }

        if (BuildConfig.DEBUG) Log.e("SpeechRecognition", "Recognition error: $errorMessage")
        onError(errorMessage)
    }

    override fun onResults(results: android.os.Bundle?) {
        isListening = false
        onListeningEnd()

        results?.let {
            val matches = it.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                // Use the best match (first result)
                val recognizedText = matches[0]
                if (BuildConfig.DEBUG) Log.d("SpeechRecognition", "Recognized: $recognizedText")
                onResult(recognizedText)
            }
        }
    }

    override fun onPartialResults(partialResults: android.os.Bundle?) {
        // Called as user is still speaking - can show partial results
        partialResults?.let {
            val matches = it.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                if (BuildConfig.DEBUG) Log.d("SpeechRecognition", "Partial: ${matches[0]}")
                // Optional: Update UI with partial results
                // onResult(matches[0])  // Uncomment to show partial results in real-time
            }
        }
    }

    override fun onEvent(eventType: Int, params: android.os.Bundle?) {
        // Called on various events during recognition
    }

    private fun getLanguageCode(context: Context): String {
        val prefs = context.getSharedPreferences("WinkerkReader_UserInfo", Context.MODE_PRIVATE)
        return prefs.getString("app_language", "af") ?: "af"
    }

    fun isCurrentlyListening(): Boolean = isListening
}