package com.menupilot.restaurant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

fun createOnDeviceVoiceInputController(context: Context): VoiceInputController {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return UnavailableVoiceInputController(
            VoiceUnavailableReason.REQUIRES_ANDROID_12,
        )
    }

    val applicationContext = context.applicationContext
    if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(applicationContext)) {
        return UnavailableVoiceInputController(
            VoiceUnavailableReason.ON_DEVICE_RECOGNIZER_MISSING,
        )
    }

    return AndroidSpeechInputController(applicationContext)
}

private class UnavailableVoiceInputController(
    reason: VoiceUnavailableReason,
) : VoiceInputController {
    private val mutableState = MutableStateFlow<VoiceInputState>(
        VoiceInputState.Unavailable(reason),
    )
    private val mutableEvents = MutableSharedFlow<VoiceInputEvent>()

    override val state: StateFlow<VoiceInputState> = mutableState.asStateFlow()
    override val events: SharedFlow<VoiceInputEvent> = mutableEvents.asSharedFlow()

    override fun start() = Unit

    override fun stop() = Unit

    override fun cancel() = Unit

    override fun close() = Unit
}

@RequiresApi(Build.VERSION_CODES.S)
private class AndroidSpeechInputController(
    private val context: Context,
) : VoiceInputController {
    private val mutableState = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    private val mutableEvents = MutableSharedFlow<VoiceInputEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val state: StateFlow<VoiceInputState> = mutableState.asStateFlow()
    override val events: SharedFlow<VoiceInputEvent> = mutableEvents.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null
    private var closed = false
    private var acceptingCallbacks = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (!acceptingCallbacks) return
            mutableState.value = VoiceInputState.Listening()
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (!acceptingCallbacks) return
            mutableState.value = VoiceInputState.Processing
        }

        override fun onError(error: Int) {
            if (!acceptingCallbacks) return
            acceptingCallbacks = false
            mutableState.value = VoiceInputState.Failed(error.toVoiceInputFailure())
        }

        override fun onResults(results: Bundle?) {
            if (!acceptingCallbacks) return
            acceptingCallbacks = false
            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .let(::firstUsableVoiceTranscript)
            if (transcript == null) {
                mutableState.value = VoiceInputState.Failed(VoiceInputFailure.NO_SPEECH)
                return
            }

            mutableState.value = VoiceInputState.Idle
            mutableEvents.tryEmit(VoiceInputEvent.FinalTranscript(transcript))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!acceptingCallbacks) return
            val transcript = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .let(::firstUsableVoiceTranscript)
                .orEmpty()
            mutableState.value = VoiceInputState.Listening(transcript)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun start() {
        if (
            closed ||
            mutableState.value is VoiceInputState.Listening ||
            mutableState.value is VoiceInputState.Processing
        ) {
            return
        }

        val activeRecognizer = try {
            recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
                recognizer = it
            }
        } catch (_: UnsupportedOperationException) {
            mutableState.value = VoiceInputState.Unavailable(
                VoiceUnavailableReason.ON_DEVICE_RECOGNIZER_MISSING,
            )
            return
        } catch (_: IllegalStateException) {
            mutableState.value = VoiceInputState.Failed(VoiceInputFailure.CLIENT)
            return
        } catch (_: SecurityException) {
            mutableState.value = VoiceInputState.Failed(VoiceInputFailure.PERMISSION)
            return
        }

        mutableState.value = VoiceInputState.Listening()
        acceptingCallbacks = true
        try {
            activeRecognizer.startListening(recognitionIntent())
        } catch (_: IllegalStateException) {
            acceptingCallbacks = false
            mutableState.value = VoiceInputState.Failed(VoiceInputFailure.CLIENT)
        } catch (_: SecurityException) {
            acceptingCallbacks = false
            mutableState.value = VoiceInputState.Failed(VoiceInputFailure.PERMISSION)
        }
    }

    override fun stop() {
        if (closed || mutableState.value !is VoiceInputState.Listening) return
        mutableState.value = VoiceInputState.Processing
        try {
            recognizer?.stopListening()
        } catch (_: IllegalStateException) {
            acceptingCallbacks = false
            mutableState.value = VoiceInputState.Failed(VoiceInputFailure.CLIENT)
        }
    }

    override fun cancel() {
        if (closed) return
        acceptingCallbacks = false
        runCatching { recognizer?.cancel() }
        mutableState.value = VoiceInputState.Idle
    }

    override fun close() {
        if (closed) return
        acceptingCallbacks = false
        runCatching { recognizer?.cancel() }
        recognizer?.destroy()
        recognizer = null
        closed = true
    }

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Int.toVoiceInputFailure(): VoiceInputFailure = when (this) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    -> VoiceInputFailure.NO_SPEECH

    SpeechRecognizer.ERROR_AUDIO -> VoiceInputFailure.AUDIO
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
    -> VoiceInputFailure.BUSY

    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    -> VoiceInputFailure.LANGUAGE

    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceInputFailure.PERMISSION
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    -> VoiceInputFailure.SERVICE

    else -> VoiceInputFailure.CLIENT
}
