package com.example.goodglasses

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.htc.viveglass.sdk.ViveGlass
import com.htc.viveglass.sdk.ViveGlassKit
import com.htc.viveglass.sdk.simulator.ViveGlassSimulator
import com.example.goodglasses.ui.tab.CameraScreen
import com.example.goodglasses.ui.theme.AppColors
import com.example.goodglasses.util.DebugLogger
import com.example.goodglasses.util.Logger
import com.example.goodglasses.util.NoOpLogger
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val KEY_EVENT_TAG = "KeyEventListener"

class MainActivity : AppCompatActivity() {

    val enableDebugLog = true

    var viveClientManager: ViveGlassKitManager? = null
    var metaGlassKitManager: MetaGlassKitManager? = null

    private val metaAndroidPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) {
                metaGlassKitManager?.onPermissionsGranted()
            } else {
                Logger.instance.e(META_TAG, "Meta glasses Android permissions denied")
            }
        }

    private var wearablesPermissionContinuation: CancellableContinuation<PermissionStatus>? = null
    private val wearablesPermissionMutex = Mutex()
    private val wearablesPermissionLauncher =
        registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
            val status = result.getOrDefault(PermissionStatus.Denied)
            wearablesPermissionContinuation?.resume(status)
            wearablesPermissionContinuation = null
        }

    private suspend fun requestWearablesPermission(permission: Permission): PermissionStatus =
        wearablesPermissionMutex.withLock {
            suspendCancellableCoroutine { continuation ->
                wearablesPermissionContinuation = continuation
                continuation.invokeOnCancellation { wearablesPermissionContinuation = null }
                wearablesPermissionLauncher.launch(permission)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.instance =
            if (enableDebugLog) DebugLogger()
            else NoOpLogger()

        enableEdgeToEdge()
        setContent {

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val glass = ViveGlass()
            val appContext = applicationContext
            val kit = ViveGlassKit(appContext)
            ViveGlassSimulator.create(appContext)
            val simulator = ViveGlassSimulator.instance()

            viveClientManager = ViveGlassKitManager(glass, kit, simulator, appContext, audioManager)
            metaGlassKitManager = MetaGlassKitManager(
                activity = this,
                scope = lifecycleScope,
                requestAndroidPermissions = { metaAndroidPermissionLauncher.launch(it) },
                requestWearablesPermission = ::requestWearablesPermission,
            )
            val manager = viveClientManager ?: return@setContent
            val metaManager = metaGlassKitManager ?: return@setContent
            SampleApp(manager, metaManager)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                Logger.instance.d(KEY_EVENT_TAG, "Volume UP key pressed")
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                Logger.instance.d(KEY_EVENT_TAG, "Volume DOWN key pressed")
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                Logger.instance.d(KEY_EVENT_TAG, "Volume UP key released")
                triggerCaptureFromRemote()
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                Logger.instance.d(KEY_EVENT_TAG, "Volume DOWN key released")
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    private fun triggerCaptureFromRemote() {
        when {
            viveClientManager?.isConnected() == true -> {
                Logger.instance.d(KEY_EVENT_TAG, "HTC glasses connected, triggering captureImage()")
                viveClientManager?.captureImage()
            }
            metaGlassKitManager?.isConnected() == true -> {
                Logger.instance.d(KEY_EVENT_TAG, "Meta glasses connected, triggering captureImage()")
                metaGlassKitManager?.captureImage()
            }
            else -> {
                Logger.instance.d(KEY_EVENT_TAG, "No source device connected, ignoring capture trigger")
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        viveClientManager?.cleanup()
        metaGlassKitManager?.cleanup()
    }
}

@Composable
fun SampleApp(viveClientManager: ViveGlassKitManager, metaGlassKitManager: MetaGlassKitManager) {
    Surface(
        color = AppColors.BgDarkPrimary,
        modifier = Modifier.fillMaxSize()
    ) {
        CameraScreen(viveClientManager, metaGlassKitManager)
    }
}
