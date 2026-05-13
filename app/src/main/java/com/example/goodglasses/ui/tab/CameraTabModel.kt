package com.example.goodglasses.ui.tab

import android.graphics.Bitmap
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goodglasses.TAG
import com.example.goodglasses.ViveGlassKitManager
import com.example.goodglasses.util.Logger
import com.example.goodglasses.data.InventoryItem
import com.example.goodglasses.data.InventoryRepository
import com.htc.viveglass.sdk.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraUiState(
    val isVideoRecording: Boolean = false,
    val isImageCapturing: Boolean = false,
    val isSimulator: Boolean = false,
    val previewRatio: Float = 1.8f,
    val isAnalyzing: Boolean = false,
    val inventoryItems: List<InventoryItem> = emptyList(),
    val analysisError: String? = null,
    val hasAnalyzed: Boolean = false
)

sealed interface CameraEvent {
    data object TakePhotoClicked : CameraEvent
    data object StartRecordingClicked : CameraEvent
    data object StopRecordingClicked : CameraEvent
}

class CameraTabModel(viveGlassKitManager: ViveGlassKitManager) : ViewModel() {
    private val log = Logger.instance
    private val manager: ViveGlassKitManager = viveGlassKitManager
    private val repository = InventoryRepository()

    private var _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState

    private val _latestImageReceived = MutableStateFlow<Bitmap?>(null)
    val latestImageReceived: StateFlow<Bitmap?> = _latestImageReceived

    init {
        viewModelScope.launch {
            manager.imageReceived.collect { bmp ->
                _latestImageReceived.value = bmp
                analyzeImage(bmp)
            }
        }
        viewModelScope.launch {
            manager.isVideoStreaming.collect { isStreaming ->
                _uiState.update { it.copy(isVideoRecording = isStreaming) }
            }
        }
        viewModelScope.launch {
            manager.isImageCapturing.collect { isCapturing ->
                _uiState.update { it.copy(isImageCapturing = isCapturing) }
            }
        }
        viewModelScope.launch {
            manager.previewRatio.collect { ratio ->
                _uiState.update { it.copy(previewRatio = ratio) }
            }
        }
        viewModelScope.launch {
            manager.isSimulator.collect { simulator ->
                _uiState.update { it.copy(isSimulator = simulator) }
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

    private fun analyzeImage(bitmap: Bitmap) {
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
                        manager.speakText(text)
                    } else {
                        manager.speakText("目前無缺貨商品")
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
            CameraEvent.TakePhotoClicked -> {
                clearPreview()
                manager.captureImage()
            }
            CameraEvent.StartRecordingClicked -> {
                clearPreview()
                manager.startVideoStreaming()
            }
            CameraEvent.StopRecordingClicked -> {
                manager.stopVideoStreaming()
            }
        }
    }

    fun clearPreview() {
        _latestImageReceived.value = null
        _uiState.update { it.copy(inventoryItems = emptyList(), analysisError = null) }
    }

    fun attachSurface(surface: Surface) {
        manager.attachPreviewSurface(surface)
    }

    fun detachSurface() {
        manager.detachAndStopPreview()
    }
}
