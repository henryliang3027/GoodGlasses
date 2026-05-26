package com.example.goodglasses.ui.tab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.goodglasses.ViveGlassKitManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GlassUiState(
    val isConnected: Boolean = false,
    val textConnectButton: String = "Connect"
)

sealed interface GlassEvent {
    data object ConnectClicked : GlassEvent
}

class GlassTabModel(viveGlassKitManager: ViveGlassKitManager) : ViewModel() {
    private val manager: ViveGlassKitManager = viveGlassKitManager
    private val _uiState = MutableStateFlow(GlassUiState())
    val uiState: StateFlow<GlassUiState> = _uiState

    init {
        manager.setSimulator(false)
        manager.connect()

        viewModelScope.launch {
            manager.connection.collect { connected ->
                _uiState.update {
                    it.copy(
                        isConnected = connected,
                        textConnectButton = if (!connected) "Connect" else "Disconnect"
                    )
                }
            }
        }
    }

    fun onEvent(event: GlassEvent) {
        when (event) {
            GlassEvent.ConnectClicked -> {
                if (!uiState.value.isConnected)
                    manager.connect()
                else
                    manager.disconnect()
            }
        }
    }
}
