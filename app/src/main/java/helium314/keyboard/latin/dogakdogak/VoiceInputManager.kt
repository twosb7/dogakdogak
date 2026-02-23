package helium314.keyboard.latin.dogakdogak

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import helium314.keyboard.latin.permissions.PermissionsUtil
import java.util.Locale

class VoiceInputManager(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onVoiceResult(text: String)
        fun onVoiceListeningStarted()
        fun onVoiceListeningStopped()
        fun onVoiceError(message: String)
        fun onRequestPermission()
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    fun startListening() {
        if (isListening) {
            stopListening()
            return
        }

        if (!PermissionsUtil.checkAllPermissionsGranted(context, Manifest.permission.RECORD_AUDIO)) {
            listener.onRequestPermission()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onVoiceError("음성 인식을 사용할 수 없습니다")
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                listener.onVoiceListeningStarted()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false
                listener.onVoiceListeningStopped()
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "인식된 음성이 없습니다"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성이 감지되지 않았습니다"
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 오류"
                    SpeechRecognizer.ERROR_AUDIO -> "오디오 오류"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        listener.onRequestPermission()
                        return
                    }
                    else -> "음성 인식 오류 ($error)"
                }
                listener.onVoiceError(message)
                destroyRecognizer()
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                listener.onVoiceListeningStopped()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (text != null) {
                    listener.onVoiceResult(text)
                } else {
                    listener.onVoiceError("인식된 음성이 없습니다")
                }
                destroyRecognizer()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            listener.onVoiceError("음성 인식을 시작할 수 없습니다")
            destroyRecognizer()
        }
    }

    fun stopListening() {
        if (isListening) {
            isListening = false
            listener.onVoiceListeningStopped()
            speechRecognizer?.stopListening()
            destroyRecognizer()
        }
    }

    fun release() {
        stopListening()
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    companion object {
        private const val TAG = "VoiceInputManager"
    }
}
