package com.menupilot.restaurant.voice

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface VoiceInputState {
    data object Idle : VoiceInputState

    data class Listening(
        val partialTranscript: String = "",
    ) : VoiceInputState

    data object Processing : VoiceInputState

    data class PermissionDenied(
        val canRequestAgain: Boolean,
    ) : VoiceInputState

    data class Unavailable(
        val reason: VoiceUnavailableReason,
    ) : VoiceInputState

    data class Failed(
        val failure: VoiceInputFailure,
    ) : VoiceInputState
}

enum class VoiceUnavailableReason {
    REQUIRES_ANDROID_12,
    ON_DEVICE_RECOGNIZER_MISSING,
}

enum class VoiceInputFailure {
    NO_SPEECH,
    AUDIO,
    BUSY,
    LANGUAGE,
    PERMISSION,
    SERVICE,
    CLIENT,
}

sealed interface VoiceInputEvent {
    data class FinalTranscript(
        val text: String,
    ) : VoiceInputEvent
}

interface VoiceInputController : AutoCloseable {
    val state: StateFlow<VoiceInputState>
    val events: SharedFlow<VoiceInputEvent>

    fun start()

    fun stop()

    fun cancel()

    override fun close()
}

internal const val MAX_VOICE_TRANSCRIPT_LENGTH = 500
private const val MAX_RECOGNITION_CANDIDATES = 5
private val repeatedWhitespace = Regex("""\s+""")

internal fun firstUsableVoiceTranscript(candidates: List<String>?): String? =
    candidates
        .orEmpty()
        .asSequence()
        .take(MAX_RECOGNITION_CANDIDATES)
        .map { candidate ->
            candidate
                .trim()
                .replace(repeatedWhitespace, " ")
                .take(MAX_VOICE_TRANSCRIPT_LENGTH)
        }
        .firstOrNull(String::isNotBlank)
