package com.menupilot.restaurant.feature.intake

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.menupilot.restaurant.app.MenuPilotUiState
import com.menupilot.restaurant.voice.VoiceInputController
import com.menupilot.restaurant.voice.VoiceInputEvent
import com.menupilot.restaurant.voice.VoiceInputState
import com.menupilot.restaurant.voice.createOnDeviceVoiceInputController

@Composable
fun VoiceIntakeHost(
    state: MenuPilotUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: () -> Unit,
    onRemoveChip: (String) -> Unit,
    onConfirmIntent: () -> Unit,
    onNewSession: () -> Unit,
    onCallStaff: () -> Unit,
    modifier: Modifier = Modifier,
    locationLabel: String = "Table 12",
    controllerFactory: (Context) -> VoiceInputController =
        ::createOnDeviceVoiceInputController,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context.applicationContext) {
        controllerFactory(context.applicationContext)
    }
    val recognitionState by controller.state.collectAsStateWithLifecycle()
    var permissionDenial by remember {
        mutableStateOf<VoiceInputState.PermissionDenied?>(null)
    }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)

    fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionDenial = null
            controller.start()
        } else {
            permissionDenial = VoiceInputState.PermissionDenied(
                canRequestAgain = context.findActivity()
                    ?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                    == true,
            )
        }
    }

    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is VoiceInputEvent.FinalTranscript -> currentOnQueryChange(event.text)
            }
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (hasMicrophonePermission()) permissionDenial = null
                }
                Lifecycle.Event.ON_STOP -> controller.cancel()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.close()
        }
    }

    val visibleVoiceState = permissionDenial ?: recognitionState
    val manualQueryChange: (String) -> Unit = { query ->
        if (
            recognitionState is VoiceInputState.Listening ||
            recognitionState is VoiceInputState.Processing
        ) {
            controller.cancel()
        }
        onQueryChange(query)
    }
    val startVoice = dropUnlessResumed {
        when {
            recognitionState is VoiceInputState.Unavailable -> Unit
            hasMicrophonePermission() -> {
                permissionDenial = null
                controller.start()
            }
            permissionDenial?.canRequestAgain != false -> {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    IntakeScreen(
        state = state,
        voiceState = visibleVoiceState,
        onBack = {
            controller.cancel()
            onBack()
        },
        onQueryChange = manualQueryChange,
        onSubmitQuery = onSubmitQuery,
        onRemoveChip = onRemoveChip,
        onConfirmIntent = onConfirmIntent,
        onNewSession = {
            controller.cancel()
            onNewSession()
        },
        onCallStaff = onCallStaff,
        locationLabel = locationLabel,
        onStartVoice = startVoice,
        onStopVoice = controller::stop,
        onCancelVoice = controller::cancel,
        onOpenVoiceSettings = {
            context.openMenuPilotPermissionSettings()
        },
        modifier = modifier,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openMenuPilotPermissionSettings() {
    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addCategory(Intent.CATEGORY_DEFAULT)
        if (this@openMenuPilotPermissionSettings !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    try {
        startActivity(settingsIntent)
    } catch (_: ActivityNotFoundException) {
        // Keyboard entry remains available when device settings cannot be opened.
    } catch (_: SecurityException) {
        // Keyboard entry remains available when device policy blocks settings.
    }
}
