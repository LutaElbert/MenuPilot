package com.menupilot.restaurant

import android.app.Application
import com.menupilot.restaurant.assistant.OnDeviceInferenceCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MenuPilotApplication : Application() {
    @Inject
    lateinit var inferenceCoordinator: OnDeviceInferenceCoordinator

    private val modelCleanupScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= RUNNING_LOW_MEMORY_LEVEL) {
            releaseRetainedModel()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        releaseRetainedModel()
    }

    private fun releaseRetainedModel() {
        modelCleanupScope.launch {
            inferenceCoordinator.releaseAll()
        }
    }

    private companion object {
        // ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW is deprecated in API 35, but Android still
        // delivers its stable value to applications that support older tablet OS versions.
        const val RUNNING_LOW_MEMORY_LEVEL = 10
    }
}
