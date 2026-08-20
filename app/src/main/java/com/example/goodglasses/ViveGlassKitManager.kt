package com.example.goodglasses

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ExifInterface
import android.media.ExifInterface.ORIENTATION_NORMAL
import android.media.ExifInterface.ORIENTATION_ROTATE_180
import android.media.ExifInterface.ORIENTATION_ROTATE_270
import android.media.ExifInterface.ORIENTATION_ROTATE_90
import android.media.MediaCodec
import android.view.Surface
import com.example.goodglasses.util.AudioDecoder
import com.example.goodglasses.util.H264Decoder
import com.example.goodglasses.util.StreamingPlayer
import com.htc.viveglass.sdk.simulator.ViveGlassSimulator
import com.htc.viveglass.sdk.CaptureEvent
import com.htc.viveglass.sdk.ConnectionState
import com.htc.viveglass.sdk.ImagePayload
import com.htc.viveglass.sdk.ImageQuality
import com.htc.viveglass.sdk.KeyEvent
import com.htc.viveglass.sdk.StreamingEvent
import com.htc.viveglass.sdk.SynthesisEvent
import com.htc.viveglass.sdk.TranscribedEvent
import com.htc.viveglass.sdk.VideoQuality
import com.htc.viveglass.sdk.ViveGlass
import com.htc.viveglass.sdk.ViveGlassKit
import com.htc.viveglass.sdk.StreamingEvent.*
import com.htc.viveglass.sdk.client.StreamingBufferCallback
import com.htc.viveglass.sdk.client.StreamingEventCallback
import com.htc.viveglass.sdk.client.ViveGlassClientCallback
import com.example.goodglasses.util.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.Locale

interface ViveGlassKitInterface {

    val connection: StateFlow<Boolean>
    val isVideoStreaming: StateFlow<Boolean>
    val isImageCapturing: StateFlow<Boolean>
    val previewRatio: StateFlow<Float>
    val isSimulator: StateFlow<Boolean>
    val imageReceived: SharedFlow<Bitmap>
    val keyEvent: SharedFlow<KeyEvent>
    fun setSimulator(isUseSimulator: Boolean)
    fun connect()
    fun disconnect()
    fun isConnected(): Boolean
    fun captureImage()
    fun startVideoStreaming()
    fun stopVideoStreaming()
    fun speakText(text: String)
    fun cleanup()
}

const val TAG: String = "ViveGlassKit"
private const val CONNECT_TIMEOUT_MS = 5000L

class ViveGlassKitManager(
    private val glass: ViveGlass,
    private val kit: ViveGlassKit,
    private val simulator: ViveGlassSimulator,
    appContext: Context,
    audioManager: AudioManager
) : ViveGlassKitInterface {

    private val log = Logger.instance
    private val _isSimulator = MutableStateFlow(false)
    override val isSimulator: StateFlow<Boolean> = _isSimulator.asStateFlow()

    private val _connection = MutableStateFlow(false)
    override val connection: StateFlow<Boolean> = _connection.asStateFlow()

    @Volatile private var isConnecting = false
    @Volatile private var connectingSinceMs = 0L

    private val _isVideoStreaming = MutableStateFlow(false)
    override val isVideoStreaming: StateFlow<Boolean> = _isVideoStreaming.asStateFlow()

    private val _isImageCapturing = MutableStateFlow(false)
    override val isImageCapturing: StateFlow<Boolean> = _isImageCapturing.asStateFlow()

    private val _previewRatio = MutableStateFlow(0.75f)
    override val previewRatio: StateFlow<Float> = _previewRatio.asStateFlow()

    private val _imageReceived = MutableSharedFlow<Bitmap>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val imageReceived: SharedFlow<Bitmap> = _imageReceived

    private val _keyEvent = MutableSharedFlow<KeyEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val keyEvent: SharedFlow<KeyEvent> = _keyEvent

    private var streamPlayer: StreamingPlayer? = null
    @Volatile private var renderSurface: Surface? = null
    private var videoDecoder: H264Decoder? = null
    private var audioDecoder = AudioDecoder(audioManager)

    // ======================
    // Callbacks
    // ======================

    private val viveGlassClientCallback = object : ViveGlassClientCallback {
        override fun onConnectionStateChanged(state: ConnectionState?) {
            log.d(TAG, "onConnectionStateChanged() state: [$state]")
            when (state) {
                ConnectionState.CONNECTING ->
                    log.d(TAG, "onConnectionStateChanged() connecting...")
                ConnectionState.CONNECTED ->
                    onConnected()
                ConnectionState.DISCONNECTED ->
                    onDisconnected()
                ConnectionState.ERROR, null -> {
                    log.e(TAG, "onConnectionStateChanged() state: [$state]")
                    isConnecting = false
                }
            }
        }

        override fun onImageCaptured(
            event: CaptureEvent?,
            payload: ImagePayload?
        ) {
            log.d(TAG, "onImageCaptured() event: [$event]")
            when (event) {
                CaptureEvent.SUCCESS -> {
                    if (payload == null) return
                    val bitmap = byteToBitmap(payload.byteArray)
                    _previewRatio.value = (bitmap.width * 1.0f) / (bitmap.height * 1.0f)
                    _imageReceived.tryEmit(bitmap)
                }
                CaptureEvent.ERROR,
                CaptureEvent.ERROR_RESOURCE_CONFLICT,
                null -> {
                    log.e(TAG, "onImageCaptured() event: [$event]")
                }
            }
            _isImageCapturing.value = false
        }

        override fun onSpeechTranscribed(event: TranscribedEvent?, text: String?) {}

        override fun onTextSpoken(event: SynthesisEvent?) {
            when (event) {
                SynthesisEvent.SUCCESS ->
                    log.d(TAG, "onTextSpoken() event: [$event]")
                SynthesisEvent.ERROR,
                SynthesisEvent.ERROR_RESOURCE_CONFLICT,
                null ->
                    log.e(TAG, "onTextSpoken() event: [$event]")
                SynthesisEvent.ERROR_UNSUPPORTED_LOCALE ->
                    log.e(TAG, "onTextSpoken() event: [$event]")
            }
        }

        override fun onKeyEvent(event: KeyEvent?) {
            if (event == null) return
            log.d(TAG, "onKeyEvent() event: [$event]")
            _keyEvent.tryEmit(event)
        }
    }

    //--------- Video Recording Callbacks ---------------
    private val streamEventCallback = object : StreamingEventCallback {
        override fun onStreamingEvent(event: StreamingEvent?) {
            val event = event ?: return
            log.d(TAG, "streamEventCallback() event = [$event]")
            when (event) {
                STARTED -> {
                    _isVideoStreaming.value = true
                    streamPlayer?.onStreamEvent(event)
                }
                STOPPED -> {
                    _isVideoStreaming.value = false
                    streamPlayer?.onStreamEvent(event)
                }
                ERROR_RESOURCE_CONFLICT,
                GLASS_OVERHEATING,
                BATTERY_LOW,
                ERROR -> {
                    log.e(TAG, "streamEventCallback() error = [$event]")
                }
            }
        }
    }

    private val audioStreamCallback = object : StreamingBufferCallback {
        override fun onReceiveBuffer(
            byteBuffer: ByteBuffer?,
            bufferInfo: MediaCodec.BufferInfo?
        ) {
            if (byteBuffer == null || bufferInfo == null) return
            streamPlayer?.onReceivedAudioBuffer(byteBuffer, bufferInfo)
        }
    }

    private val videoStreamCallback = object : StreamingBufferCallback {
        override fun onReceiveBuffer(
            byteBuffer: ByteBuffer?,
            bufferInfo: MediaCodec.BufferInfo?
        ) {
            if (byteBuffer == null || bufferInfo == null) return
            streamPlayer?.onReceivedVideoBuffer(byteBuffer, bufferInfo)
        }
    }

    private val playerCallback = object : StreamingPlayer.StreamingPlayerCallback {
        override fun onPlaybackEvent(event: StreamingPlayer.PlaybackEvent) {
            when (event) {
                StreamingPlayer.PlaybackEvent.EndOfStream -> {
                    _isVideoStreaming.value = false
                    log.d(TAG, "onPlaybackEvent() EOS")
                }
                is StreamingPlayer.PlaybackEvent.Error -> TODO()
                StreamingPlayer.PlaybackEvent.Started -> {
                    log.d(TAG, "onPlaybackEvent() Started...")
                    val player = streamPlayer ?: return
                    _previewRatio.value = player.getFrameRatio()
                }
                StreamingPlayer.PlaybackEvent.Stopped -> TODO()
            }
        }
    }

    // ==============================
    // Decoder / StreamingPlayer
    // ==============================

    fun attachPreviewSurface(surface: Surface) {
        if (renderSurface != null && renderSurface !== surface)
            videoDecoder?.stop()

        renderSurface = surface

        log.d(TAG, "attachPreviewSurface() Surface attached.")
        createVideoDecoder()
    }

    fun detachAndStopPreview() {
        if (renderSurface != null) {
            stopVideoStreaming()
        }
        log.d(TAG, "detachAndStopPreview() Surface detached.")
    }

    private fun createVideoDecoder() {
        val surf = renderSurface ?: return

        videoDecoder = H264Decoder(surf)
        createStreamPlayer()
    }

    private fun createStreamPlayer() {
        val v = videoDecoder ?: return
        val player = StreamingPlayer(v, audioDecoder, callbacks = playerCallback)
        streamPlayer = player

        log.d(TAG, "Decoder created and started")
    }

    // =======================
    // ViveGlassKit
    // =======================

    override fun connect() {
        val now = System.currentTimeMillis()
        if (isConnecting && now - connectingSinceMs < CONNECT_TIMEOUT_MS) {
            log.d(TAG, "connect() already connecting, skip")
            return
        }
        if (isConnecting) {
            log.d(TAG, "connect() previous attempt timed out, retrying")
        }
        log.d(TAG, "connect()")
        isConnecting = true
        connectingSinceMs = now
        glass.connect(viveGlassClientCallback)
    }

    override fun disconnect() {
        log.d(TAG, "disconnect()")
        isConnecting = false
        glass.disconnect()
    }

    private fun onConnected() {
        isConnecting = false
        _connection.value = true
    }

    private fun onDisconnected() {
        isConnecting = false
        _connection.value = false
        if (_isVideoStreaming.value)
            stopVideoStreaming()
    }

    override fun isConnected(): Boolean {
        return glass.isConnected
    }

    override fun setSimulator(isUseSimulator: Boolean) {
        if (_isSimulator.value == isUseSimulator && ViveGlass.adapter != null) return
        if (glass.isConnected) glass.disconnect()

        _isSimulator.value = isUseSimulator
        if (isUseSimulator)
            ViveGlass.adapter = simulator
        else
            ViveGlass.adapter = kit
    }

    override fun captureImage() {
        val result = glass.captureImage(ImageQuality.DEFAULT)
        _isImageCapturing.value = true
        log.d(TAG, "takePhoto result : $result")
    }

    override fun startVideoStreaming() {
        glass.startVideoStreaming(
            VideoQuality.DEFAULT,
            videoStreamCallback,
            audioStreamCallback,
            streamEventCallback
        )
    }

    override fun stopVideoStreaming() {
        glass.stopVideoStreaming()
    }

    override fun speakText(text: String) {
        glass.speakText(text, glass.userPreferredLocale)
    }

    override fun cleanup() {
        streamPlayer?.release()
    }

    // =======================
    // =======================
    // HEIC to bitmap
    // =======================

    private fun byteToBitmap(data: ByteArray): Bitmap {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        val orientation = readHeicExif(data)["Orientation"] ?: return bitmap
        return rotateBitmap(bitmap, orientation)
    }

    private fun readHeicExif(bytes: ByteArray): Map<String, String?> {
        ByteArrayInputStream(bytes).use { input ->
            val exif = ExifInterface(input)

            return mapOf(
                "DateTime" to exif.getAttribute(ExifInterface.TAG_DATETIME),
                "Make" to exif.getAttribute(ExifInterface.TAG_MAKE),
                "Model" to exif.getAttribute(ExifInterface.TAG_MODEL),
                "Orientation" to exif.getAttribute(ExifInterface.TAG_ORIENTATION),
                "GPS Lat" to exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE),
                "GPS Lon" to exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            )
        }
    }

    private fun rotateBitmap(source: Bitmap, orientation: String): Bitmap {
        val degrees = when (orientation) {
            ORIENTATION_ROTATE_180.toString() -> 180f
            ORIENTATION_ROTATE_90.toString() -> 90f
            ORIENTATION_ROTATE_270.toString() -> 270f
            ORIENTATION_NORMAL.toString() -> 0f
            else -> 0f
        }

        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true
        )
    }
}
