package com.menupilot.restaurant.assistant

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-wide ownership boundary for native on-device model engines.
 *
 * Every LiteRT-LM path must acquire this coordinator before it creates or uses an engine.
 * FunctionGemma may retain one engine between requests through [withReusableEngine]. One-shot
 * Qwen paths use [withExclusiveEngine], which closes that retained engine before their block can
 * create another one. Both the mutex and resident slot are process-wide so separate coordinator
 * instances cannot accidentally permit two native engines.
 */
@Singleton
class OnDeviceInferenceCoordinator @Inject constructor() {
    suspend fun <T> withExclusiveEngine(
        modelId: String,
        block: suspend () -> T,
    ): T = PROCESS_ENGINE_MUTEX.withLock {
        require(MODEL_ID_PATTERN.matches(modelId)) { "Invalid on-device model ID." }
        evictResidentLocked()
        block()
    }

    /**
     * Runs [block] with the only reusable native engine allowed in this process.
     *
     * [key] must include every artifact and engine-configuration property that can make an
     * initialized engine stale. A matching resident is reused. A different key is closed before
     * [create] runs. A rejected model output keeps the engine because the request conversation
     * has already been closed and only the app-level contract failed. Native/runtime failures and
     * cancellation evict the engine because its inference state is no longer known to be reusable.
     */
    suspend fun <R : Any, T> withReusableEngine(
        key: ReusableEngineKey,
        create: suspend () -> R,
        close: suspend (R) -> Unit,
        block: suspend (R) -> T,
    ): T = PROCESS_ENGINE_MUTEX.withLock {
        key.validate()
        val matching = residentSlot?.takeIf { it.key == key }
        val slot = if (matching != null) {
            matching
        } else {
            evictResidentLocked()
            val resource = create()
            ResidentEngineSlot(
                key = key,
                resource = resource,
                close = { retained ->
                    @Suppress("UNCHECKED_CAST")
                    close(retained as R)
                },
            ).also { residentSlot = it }
        }

        @Suppress("UNCHECKED_CAST")
        val resource = slot.resource as R
        try {
            block(resource)
        } catch (error: ModelGenerationException.OutputRejected) {
            throw error
        } catch (error: Throwable) {
            try {
                evictResidentLocked(expected = slot)
            } catch (closeError: Throwable) {
                error.addSuppressed(closeError)
            }
            throw error
        }
    }

    /**
     * Releases the retained engine after active inference finishes.
     *
     * This is safe to call from process-background or memory-pressure handling and from test
     * cleanup. A caller cancelled while waiting for the mutex never closes the active resident.
     */
    suspend fun releaseAll() {
        PROCESS_ENGINE_MUTEX.withLock {
            evictResidentLocked()
        }
    }

    private suspend fun evictResidentLocked(
        expected: ResidentEngineSlot? = null,
    ) {
        val resident = residentSlot ?: return
        if (expected != null && resident !== expected) return
        try {
            withContext(NonCancellable) {
                resident.close(resident.resource)
            }
            residentSlot = null
        } catch (error: CancellationException) {
            // NonCancellable cleanup should not normally reach this branch. Retain ownership if
            // native close did not complete, so another model is never opened concurrently.
            throw error
        } catch (error: ModelGenerationException.RuntimeFailed) {
            throw error
        } catch (error: Exception) {
            throw ModelGenerationException.RuntimeFailed(error)
        } catch (error: LinkageError) {
            throw ModelGenerationException.RuntimeFailed(error)
        }
    }

    data class ReusableEngineKey(
        val modelId: String,
        val artifactPath: String,
        val artifactSha256: String,
        val artifactSizeBytes: Long,
        val artifactLastModifiedMillis: Long,
        val configurationId: String,
    ) {
        internal fun validate() {
            require(MODEL_ID_PATTERN.matches(modelId)) { "Invalid on-device model ID." }
            require(artifactPath.isNotBlank()) { "Reusable engine path must not be blank." }
            require(SHA256_PATTERN.matches(artifactSha256)) {
                "Reusable engine SHA-256 must be lowercase hexadecimal."
            }
            require(artifactSizeBytes > 0L) { "Reusable engine size must be positive." }
            require(artifactLastModifiedMillis >= 0L) {
                "Reusable engine last-modified value must not be negative."
            }
            require(configurationId.isNotBlank()) {
                "Reusable engine configuration must not be blank."
            }
        }
    }

    private data class ResidentEngineSlot(
        val key: ReusableEngineKey,
        val resource: Any,
        val close: suspend (Any) -> Unit,
    )

    private companion object {
        val PROCESS_ENGINE_MUTEX = Mutex()
        val MODEL_ID_PATTERN = Regex("""[a-z0-9][a-z0-9._-]{0,63}""")
        val SHA256_PATTERN = Regex("""[0-9a-f]{64}""")
        var residentSlot: ResidentEngineSlot? = null
    }
}
