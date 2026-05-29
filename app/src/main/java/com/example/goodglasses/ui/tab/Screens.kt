package com.example.goodglasses.ui.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.goodglasses.ViveGlassKitManager
import com.example.goodglasses.ui.components.ViveHeaderSurface
import com.example.goodglasses.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(clientManager: ViveGlassKitManager) {
    val vm = remember(clientManager) { CameraTabModel(clientManager) }
    val states by vm.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showPhoneCamera by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = AppColors.BgDarkSecondary) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "辨識模式",
                        color = AppColors.TextGray300,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ModeToggle(
                        selectedMode = states.analysisMode,
                        onModeSelected = { vm.onEvent(CameraEvent.ModeChanged(it)) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        ) {
            ViveHeaderSurface(
                isConnected = states.isConnected,
                connectButtonText = states.textConnectButton,
                onConnectClick = { vm.onEvent(CameraEvent.ConnectClicked) },
                onMenuClick = { scope.launch { drawerState.open() } }
            ) {
                CameraTab(vm::onEvent, vm, onOpenPhoneCamera = { showPhoneCamera = true })
            }
        }

        if (showPhoneCamera) {
            PhoneCameraScreen(
                onPhotoCaptured = { bitmap ->
                    showPhoneCamera = false
                    vm.onPhoneCapture(bitmap)
                },
                onDismiss = { showPhoneCamera = false }
            )
        }
    }
}
