package com.example.goodglasses.ui.tab

import android.graphics.Bitmap
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goodglasses.TAG
import com.example.goodglasses.MetaGlassKitManager
import com.example.goodglasses.ViveGlassKitManager
import com.example.goodglasses.util.Logger
import com.example.goodglasses.data.ExpiryItem
import com.example.goodglasses.data.InventoryItem
import com.example.goodglasses.data.InventoryRepository
import com.example.goodglasses.ui.components.DeviceSource
import com.htc.viveglass.sdk.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AnalysisMode { INVENTORY, EXPIRY }

data class CameraUiState(
    val isConnected: Boolean = false,
    val textConnectButton: String = "Connect",
    val isVideoRecording: Boolean = false,
    val isImageCapturing: Boolean = false,
    val previewRatio: Float = 0.75f,
    val isAnalyzing: Boolean = false,
    val analysisMode: AnalysisMode = AnalysisMode.EXPIRY,
    val inventoryItems: List<InventoryItem> = emptyList(),
    val expiryItems: List<ExpiryItem> = emptyList(),
    val analysisError: String? = null,
    val hasAnalyzed: Boolean = false,
    val deviceSource: DeviceSource = DeviceSource.HTC,
    val isPairing: Boolean = false
)

sealed interface CameraEvent {
    data object ConnectClicked : CameraEvent
    data object TakePhotoClicked : CameraEvent
    data object StartRecordingClicked : CameraEvent
    data object StopRecordingClicked : CameraEvent
    data class ModeChanged(val mode: AnalysisMode) : CameraEvent
    data class DeviceSourceChanged(val source: DeviceSource) : CameraEvent
}

class CameraTabModel(
    viveGlassKitManager: ViveGlassKitManager,
    private val metaGlassKitManager: MetaGlassKitManager,
) : ViewModel() {
    private val log = Logger.instance
    private val manager: ViveGlassKitManager = viveGlassKitManager
    private val repository = InventoryRepository()

    private var _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    private val _latestImageReceived = MutableStateFlow<Bitmap?>(null)
    val latestImageReceived: StateFlow<Bitmap?> = _latestImageReceived

    private val _appliedExpiryServer = MutableStateFlow(repository.getExpiryServerAddress())
    val appliedExpiryServer: StateFlow<String> = _appliedExpiryServer

    init {
        manager.setSimulator(false)
        manager.connect()

        viewModelScope.launch {
            manager.connection.collect { connected ->
                if (_uiState.value.deviceSource == DeviceSource.HTC) {
                    _uiState.update {
                        it.copy(
                            isConnected = connected,
                            textConnectButton = if (!connected) "Connect" else "Disconnect"
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            metaGlassKitManager.connection.collect { connected ->
                if (_uiState.value.deviceSource == DeviceSource.META) {
                    _uiState.update {
                        it.copy(
                            isConnected = connected,
                            textConnectButton = if (!connected) "Connect" else "Disconnect"
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            metaGlassKitManager.isPairing.collect { pairing ->
                if (_uiState.value.deviceSource == DeviceSource.META) {
                    _uiState.update { it.copy(isPairing = pairing) }
                }
            }
        }
        viewModelScope.launch {
            manager.imageReceived.collect { bmp -> handleImageReceived(bmp) }
        }
        viewModelScope.launch {
            metaGlassKitManager.imageReceived.collect { bmp -> handleImageReceived(bmp) }
        }
        viewModelScope.launch {
            manager.isVideoStreaming.collect { isStreaming ->
                _uiState.update { it.copy(isVideoRecording = isStreaming) }
            }
        }
        viewModelScope.launch {
            manager.isImageCapturing.collect { isCapturing ->
                if (_uiState.value.deviceSource == DeviceSource.HTC) {
                    _uiState.update { it.copy(isImageCapturing = isCapturing) }
                }
            }
        }
        viewModelScope.launch {
            metaGlassKitManager.isImageCapturing.collect { isCapturing ->
                if (_uiState.value.deviceSource == DeviceSource.META) {
                    _uiState.update { it.copy(isImageCapturing = isCapturing) }
                }
            }
        }
        viewModelScope.launch {
            manager.previewRatio.collect { ratio ->
                _uiState.update { it.copy(previewRatio = ratio) }
            }
        }
        viewModelScope.launch {
            manager.keyEvent.collect { event ->
                if (event == KeyEvent.AIBUTTON) {
                    clearPreview()
                    manager.captureImage()
                }
            }
        }
    }

    private fun speak(text: String) {
        if (uiState.value.deviceSource == DeviceSource.HTC) {
            manager.speakText(text)
        }
    }

    private fun handleImageReceived(bmp: Bitmap) {
        _latestImageReceived.value = bmp
        log.d(TAG, "imageReceived: width=${bmp.width}, height=${bmp.height}")
        when (uiState.value.analysisMode) {
            AnalysisMode.INVENTORY -> analyzeInventory(bmp)
            AnalysisMode.EXPIRY -> analyzeExpiry(bmp)
        }
    }

    private fun analyzeInventory(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, inventoryItems = emptyList(), analysisError = null) }
            repository.analyzeImage(bitmap).fold(
                onSuccess = { items ->
                    _uiState.update { it.copy(isAnalyzing = false, inventoryItems = items, hasAnalyzed = true) }
                    if (items.isNotEmpty()) {
                        val totalOutOfStock = items.sumOf { it.outOfStock.size }
                        val text = if (totalOutOfStock <= 3) {
                            items.joinToString(", ") { item ->
                                val label = if (item.position == "top") "上層" else "下層"
                                val names = item.outOfStock.joinToString("、")
                                "${label}缺貨商品 $names"
                            }
                        } else {
                            "多項商品缺貨, 請參考手機上的清單"
                        }
                        log.d(TAG, "speakText: $text")
                        speak(text)
                    } else {
                        speak("目前無缺貨商品")
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isAnalyzing = false, analysisError = error.message, hasAnalyzed = true) }
                }
            )
        }
    }

    private fun analyzeExpiry(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, expiryItems = emptyList(), analysisError = null) }
            repository.analyzeExpiry(bitmap).fold(
                onSuccess = { items ->
                    _uiState.update { it.copy(isAnalyzing = false, expiryItems = items, hasAnalyzed = true) }
                    if (items.isNotEmpty()) {
                        val text =
                            "偵測到 ${items.size} 項商品效期，請參考手機上的清單"
                        
                        log.d(TAG, "speakText: $text")
                        speak(text)
                    } else {
                        speak("目前無效期資料")
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isAnalyzing = false, analysisError = error.message, hasAnalyzed = true) }
                }
            )
        }
    }

    fun onEvent(event: CameraEvent) {
        when (event) {
            CameraEvent.ConnectClicked -> {
                when (uiState.value.deviceSource) {
                    DeviceSource.HTC ->
                        if (!uiState.value.isConnected) manager.connect() else manager.disconnect()
                    DeviceSource.META ->
                        if (!uiState.value.isConnected) metaGlassKitManager.connect() else metaGlassKitManager.disconnect()
                }
            }
            CameraEvent.TakePhotoClicked -> {
                clearPreview()
                when (uiState.value.deviceSource) {
                    DeviceSource.HTC -> manager.captureImage()
                    DeviceSource.META -> metaGlassKitManager.captureImage()
                }
            }
            CameraEvent.StartRecordingClicked -> {
                if (uiState.value.deviceSource == DeviceSource.HTC) {
                    clearPreview()
                    manager.startVideoStreaming()
                }
            }
            CameraEvent.StopRecordingClicked -> {
                if (uiState.value.deviceSource == DeviceSource.HTC) {
                    manager.stopVideoStreaming()
                }
            }
            is CameraEvent.ModeChanged -> {
                _uiState.update {
                    it.copy(
                        analysisMode = event.mode,
                        inventoryItems = emptyList(),
                        expiryItems = emptyList(),
                        analysisError = null,
                        hasAnalyzed = false
                    )
                }
            }
            is CameraEvent.DeviceSourceChanged -> {
                val newSource = event.source
                if (newSource == uiState.value.deviceSource) return

                when (newSource) {
                    DeviceSource.META -> manager.disconnect()
                    DeviceSource.HTC -> metaGlassKitManager.disconnect()
                }
                _uiState.update {
                    it.copy(
                        deviceSource = newSource,
                        isConnected = false,
                        textConnectButton = "Connect",
                        isImageCapturing = false,
                        isPairing = false
                    )
                }
            }
        }
    }

    fun onPhoneCapture(bitmap: Bitmap) {
        _latestImageReceived.value = bitmap
        _uiState.update { it.copy(inventoryItems = emptyList(), expiryItems = emptyList(), analysisError = null, hasAnalyzed = false) }
        when (uiState.value.analysisMode) {
            AnalysisMode.INVENTORY -> analyzeInventory(bitmap)
            AnalysisMode.EXPIRY -> analyzeExpiry(bitmap)
        }
    }

    fun setExpiryServerAddress(ip: String, port: String) {
        repository.setExpiryServer(ip, port)
        _appliedExpiryServer.value = repository.getExpiryServerAddress()
    }

    fun clearPreview() {
        _latestImageReceived.value = null
        _uiState.update { it.copy(inventoryItems = emptyList(), expiryItems = emptyList(), analysisError = null) }
    }

    fun attachSurface(surface: Surface) {
        manager.attachPreviewSurface(surface)
    }

    fun detachSurface() {
        manager.detachAndStopPreview()
    }
}
