package com.example.goodglasses

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.content.ContextCompat
import com.example.goodglasses.util.Logger
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addStream
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.DeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val META_TAG: String = "MetaGlassKit"

private val META_ANDROID_PERMISSIONS =
    arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.CAMERA,
        Manifest.permission.INTERNET,
    )

class MetaGlassKitManager(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val requestAndroidPermissions: (Array<String>) -> Unit,
    private val requestWearablesPermission: suspend (Permission) -> PermissionStatus,
) {
    private val log = Logger.instance

    private val _connection = MutableStateFlow(false)
    val connection: StateFlow<Boolean> = _connection.asStateFlow()

    private val _isPairing = MutableStateFlow(false)
    val isPairing: StateFlow<Boolean> = _isPairing.asStateFlow()

    private val _isImageCapturing = MutableStateFlow(false)
    val isImageCapturing: StateFlow<Boolean> = _isImageCapturing.asStateFlow()

    private val _imageReceived = MutableSharedFlow<Bitmap>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val imageReceived: SharedFlow<Bitmap> = _imageReceived

    private val deviceSelector: DeviceSelector = AutoDeviceSelector()

    private var session: DeviceSession? = null
    private var stream: Stream? = null
    private var connectJob: Job? = null
    private var sessionStateJob: Job? = null
    private var streamStateJob: Job? = null
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        Wearables.startRegistration(activity)
    }

    fun connect() {
        if (!hasRequiredAndroidPermissions()) {
            requestAndroidPermissions(META_ANDROID_PERMISSIONS)
            return
        }
        onPermissionsGranted()
    }

    fun onPermissionsGranted() {
        register()
        connectJob?.cancel()
        _isPairing.value = true
        connectJob = scope.launch {
            // checkPermissionStatus / requestPermission both require an actively paired
            // device — calling them before Meta AI registration finishes and a device is
            // selected fails internally and looks like "permission is null".
            log.d(META_TAG, "Waiting for an active Meta glasses device...")
            val device = deviceSelector.activeDeviceFlow().filterNotNull().first()
            log.d(META_TAG, "Active device found: $device")

            val result = Wearables.checkPermissionStatus(Permission.CAMERA)
            result.onFailure { error, _ ->
                log.e(META_TAG, "checkPermissionStatus failed: ${error.description}")
            }

            val granted =
                if (result.getOrNull() == PermissionStatus.Granted) {
                    true
                } else {
                    requestWearablesPermission(Permission.CAMERA) == PermissionStatus.Granted
                }

            if (!granted) {
                log.e(META_TAG, "Glasses camera permission not granted")
                _isPairing.value = false
                return@launch
            }
            openSession()
        }
    }

    fun disconnect() {
        scope.launch {
            connectJob?.cancel()
            connectJob = null
            sessionStateJob?.cancel()
            sessionStateJob = null
            streamStateJob?.cancel()
            streamStateJob = null
            stream?.stop()
            stream = null
            session?.stop()
            session = null
            _connection.value = false
            _isPairing.value = false
        }
    }

    fun isConnected(): Boolean = _connection.value

    fun captureImage() {
        val currentStream = stream
        if (currentStream == null) {
            log.e(META_TAG, "captureImage() called with no active stream")
            return
        }
        scope.launch {
            _isImageCapturing.value = true
            currentStream.capturePhoto()
                .onSuccess { photoData -> _imageReceived.tryEmit(photoData.toBitmap()) }
                .onFailure { error, _ -> log.e(META_TAG, "capturePhoto failed: ${error.description}") }
            _isImageCapturing.value = false
        }
    }

    fun cleanup() {
        disconnect()
    }

    private fun openSession() {
        if (session != null) return

        Wearables.createSession(deviceSelector)
            .onSuccess { newSession ->
                session = newSession
                observeSessionErrors(newSession)
                observeSessionState(newSession)
                newSession.start()
            }
            .onFailure { error, _ ->
                log.e(META_TAG, "createSession failed: ${error.description}")
                _isPairing.value = false
            }
    }

    private fun observeSessionErrors(deviceSession: DeviceSession) {
        scope.launch {
            deviceSession.errors.collect { error ->
                log.e(META_TAG, "session error: ${error.description}")
            }
        }
    }

    private fun observeSessionState(deviceSession: DeviceSession) {
        sessionStateJob?.cancel()
        sessionStateJob = scope.launch {
            deviceSession.state.collect { state ->
                log.d(META_TAG, "session state: $state")
                if (state == DeviceSessionState.STARTED && stream == null) {
                    attachStream(deviceSession)
                }
            }
        }
    }

    private fun attachStream(deviceSession: DeviceSession) {
        deviceSession.addStream(StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 24))
            .onSuccess { newStream ->
                stream = newStream
                streamStateJob?.cancel()
                streamStateJob = scope.launch {
                    newStream.state.collect { state ->
                        log.d(META_TAG, "stream state: $state")
                        val streaming = state == StreamState.STREAMING
                        _connection.value = streaming
                        if (streaming) _isPairing.value = false
                    }
                }
                scope.launch {
                    newStream.errorStream.collect { error ->
                        log.e(META_TAG, "stream error: ${error.description}")
                    }
                }
                newStream.start()
            }
            .onFailure { error, _ ->
                log.e(META_TAG, "addStream failed: ${error.description}")
                _isPairing.value = false
            }
    }

    private fun PhotoData.toBitmap(): Bitmap =
        when (this) {
            is PhotoData.Bitmap -> bitmap
            is PhotoData.HEIC -> {
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }

    private fun hasRequiredAndroidPermissions(): Boolean =
        META_ANDROID_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
}
