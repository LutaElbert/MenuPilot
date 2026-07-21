package com.menupilot.restaurant.assistant

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OnDeviceInferenceCoordinatorTest {
    @Test
    fun `matching FunctionGemma key reuses one resident resource`() = runTest {
        val coordinator = cleanCoordinator()
        val tracker = FakeEngineTracker()
        val key = functionGemmaKey()
        var first: FakeEngine? = null
        var second: FakeEngine? = null

        try {
            first = coordinator.withReusableEngine(
                key = key,
                create = { tracker.open("functiongemma") },
                close = tracker::close,
            ) { it }
            second = coordinator.withReusableEngine(
                key = key,
                create = { tracker.open("functiongemma") },
                close = tracker::close,
            ) { it }

            assertSame(first, second)
            assertEquals(1, tracker.openCount)
            assertEquals(0, tracker.closeCount)
            assertEquals(1, tracker.activeCount)
        } finally {
            coordinator.releaseAll()
        }

        assertEquals(1, tracker.closeCount)
        assertEquals(0, tracker.activeCount)
    }

    @Test
    fun `one-shot Qwen closes FunctionGemma before opening and never overlaps`() = runTest {
        val coordinator = cleanCoordinator()
        val separatelyConstructedQwenCoordinator = OnDeviceInferenceCoordinator()
        val tracker = FakeEngineTracker()

        try {
            coordinator.withReusableEngine(
                key = functionGemmaKey(),
                create = { tracker.open("functiongemma") },
                close = tracker::close,
            ) { Unit }

            separatelyConstructedQwenCoordinator.withExclusiveEngine("qwen3_0_6b") {
                val qwen = tracker.open("qwen")
                tracker.events += "qwen_inference"
                tracker.close(qwen)
            }

            assertEquals(
                listOf(
                    "open:functiongemma",
                    "close:functiongemma",
                    "open:qwen",
                    "qwen_inference",
                    "close:qwen",
                ),
                tracker.events,
            )
            assertEquals(1, tracker.maximumActiveCount)
            assertEquals(0, tracker.activeCount)
        } finally {
            coordinator.releaseAll()
        }
    }

    @Test
    fun `semantic output rejection retains verified engine for next request`() = runTest {
        val coordinator = cleanCoordinator()
        val tracker = FakeEngineTracker()
        var rejectedEngine: FakeEngine? = null
        var reusedEngine: FakeEngine? = null

        try {
            try {
                coordinator.withReusableEngine<FakeEngine, Unit>(
                    key = functionGemmaKey(),
                    create = { tracker.open("functiongemma") },
                    close = tracker::close,
                ) { engine ->
                    rejectedEngine = engine
                    throw ModelGenerationException.OutputRejected("bad_route")
                }
                fail("Expected the output rejection to propagate.")
            } catch (error: ModelGenerationException.OutputRejected) {
                assertEquals("bad_route", error.rejectionReason)
            }

            reusedEngine = coordinator.withReusableEngine(
                key = functionGemmaKey(),
                create = { tracker.open("functiongemma") },
                close = tracker::close,
            ) { it }

            assertSame(rejectedEngine, reusedEngine)
            assertEquals(1, tracker.openCount)
            assertEquals(0, tracker.closeCount)
            assertEquals(1, tracker.activeCount)
        } finally {
            coordinator.releaseAll()
        }
    }

    @Test
    fun `runtime failure evicts resident and next request recreates it`() = runTest {
        val coordinator = cleanCoordinator()
        val tracker = FakeEngineTracker()
        var failedEngine: FakeEngine? = null
        var replacement: FakeEngine? = null

        try {
            try {
                coordinator.withReusableEngine<FakeEngine, Unit>(
                    key = functionGemmaKey(),
                    create = { tracker.open("functiongemma") },
                    close = tracker::close,
                ) { engine ->
                    failedEngine = engine
                    throw ModelGenerationException.RuntimeFailed(
                        IllegalStateException("native inference failed"),
                    )
                }
                fail("Expected the runtime failure to propagate.")
            } catch (error: ModelGenerationException.RuntimeFailed) {
                assertEquals("native inference failed", error.cause?.message)
            }

            replacement = coordinator.withReusableEngine(
                key = functionGemmaKey(),
                create = { tracker.open("functiongemma") },
                close = tracker::close,
            ) { it }

            assertNotSame(failedEngine, replacement)
            assertEquals(2, tracker.openCount)
            assertEquals(1, tracker.closeCount)
            assertEquals(1, tracker.activeCount)
        } finally {
            coordinator.releaseAll()
        }
    }

    @Test
    fun `inference cancellation evicts resident and propagates cancellation`() = runTest {
        val coordinator = cleanCoordinator()
        val tracker = FakeEngineTracker()

        try {
            try {
                coordinator.withReusableEngine<FakeEngine, Unit>(
                    key = functionGemmaKey(),
                    create = { tracker.open("functiongemma") },
                    close = tracker::close,
                ) {
                    throw CancellationException("guest left")
                }
                fail("Expected cancellation to propagate.")
            } catch (error: CancellationException) {
                assertEquals("guest left", error.message)
            }

            assertEquals(1, tracker.openCount)
            assertEquals(1, tracker.closeCount)
            assertEquals(0, tracker.activeCount)
        } finally {
            coordinator.releaseAll()
        }
    }

    @Test
    fun `artifact fingerprint change closes stale resident before recreation`() = runTest {
        val coordinator = cleanCoordinator()
        val tracker = FakeEngineTracker()
        val originalKey = functionGemmaKey(lastModifiedMillis = 100L)
        val changedKey = originalKey.copy(artifactLastModifiedMillis = 101L)
        var original: FakeEngine? = null
        var replacement: FakeEngine? = null

        try {
            original = coordinator.withReusableEngine(
                key = originalKey,
                create = { tracker.open("functiongemma-v1") },
                close = tracker::close,
            ) { it }
            replacement = coordinator.withReusableEngine(
                key = changedKey,
                create = { tracker.open("functiongemma-v2") },
                close = tracker::close,
            ) { it }

            assertNotSame(original, replacement)
            assertEquals(
                listOf(
                    "open:functiongemma-v1",
                    "close:functiongemma-v1",
                    "open:functiongemma-v2",
                ),
                tracker.events,
            )
            assertEquals(1, tracker.maximumActiveCount)
        } finally {
            coordinator.releaseAll()
        }
    }

    @Test
    fun `caller cancelled while waiting does not evict active resident`() = runTest {
        val coordinator = cleanCoordinator()
        val tracker = FakeEngineTracker()
        val activeEntered = CompletableDeferred<Unit>()
        val releaseActive = CompletableDeferred<Unit>()
        val qwenEntered = CompletableDeferred<Unit>()

        try {
            val active = async {
                coordinator.withReusableEngine(
                    key = functionGemmaKey(),
                    create = { tracker.open("functiongemma") },
                    close = tracker::close,
                ) {
                    activeEntered.complete(Unit)
                    releaseActive.await()
                }
            }
            activeEntered.await()

            val waitingQwen = async {
                coordinator.withExclusiveEngine("qwen3_0_6b") {
                    qwenEntered.complete(Unit)
                }
            }
            testScheduler.runCurrent()
            assertFalse(qwenEntered.isCompleted)

            waitingQwen.cancelAndJoin()
            releaseActive.complete(Unit)
            active.await()

            val reused = coordinator.withReusableEngine(
                key = functionGemmaKey(),
                create = { tracker.open("functiongemma") },
                close = tracker::close,
            ) { it }

            assertEquals("functiongemma", reused.name)
            assertEquals(1, tracker.openCount)
            assertEquals(0, tracker.closeCount)
            assertEquals(1, tracker.activeCount)
        } finally {
            coordinator.releaseAll()
        }
    }

    private suspend fun cleanCoordinator(): OnDeviceInferenceCoordinator =
        OnDeviceInferenceCoordinator().also { it.releaseAll() }

    private fun functionGemmaKey(
        lastModifiedMillis: Long = 100L,
    ): OnDeviceInferenceCoordinator.ReusableEngineKey =
        OnDeviceInferenceCoordinator.ReusableEngineKey(
            modelId = "functiongemma_mobile_actions",
            artifactPath = "/models/mobile-actions.litertlm",
            artifactSha256 = "a".repeat(64),
            artifactSizeBytes = 285_561_008L,
            artifactLastModifiedMillis = lastModifiedMillis,
            configurationId = "litertlm-android:0.14.0|cpu|threads=4|tokens=1024",
        )

    private class FakeEngineTracker {
        val events = mutableListOf<String>()
        var openCount = 0
            private set
        var closeCount = 0
            private set
        var activeCount = 0
            private set
        var maximumActiveCount = 0
            private set

        fun open(name: String): FakeEngine {
            openCount += 1
            activeCount += 1
            maximumActiveCount = maxOf(maximumActiveCount, activeCount)
            events += "open:$name"
            return FakeEngine(name)
        }

        suspend fun close(engine: FakeEngine) {
            assertTrue("Engine ${engine.name} was already closed.", !engine.closed)
            engine.closed = true
            closeCount += 1
            activeCount -= 1
            events += "close:${engine.name}"
        }
    }

    private data class FakeEngine(
        val name: String,
        var closed: Boolean = false,
    )
}
