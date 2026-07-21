package com.example.goodglasses.ui.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.goodglasses.MetaGlassKitManager
import com.example.goodglasses.ViveGlassKitManager
import com.example.goodglasses.ui.components.ViveHeaderSurface
import com.example.goodglasses.ui.theme.AppColors
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(clientManager: ViveGlassKitManager, metaGlassKitManager: MetaGlassKitManager) {
    val vm = remember(clientManager, metaGlassKitManager) { CameraTabModel(clientManager, metaGlassKitManager) }
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

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "辨識伺服器",
                        color = AppColors.TextGray300,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val appliedServer by vm.appliedServerAddress.collectAsStateWithLifecycle()
                    var ipText by remember { mutableStateOf(appliedServer.substringBefore(":")) }
                    var portText by remember { mutableStateOf(appliedServer.substringAfter(":")) }

                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)) {
                        OutlinedTextField(
                            value = ipText,
                            onValueChange = { ipText = it },
                            label = { Text("IP Address") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it },
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { vm.setServerAddress(ipText.trim(), portText.trim()) },
                        enabled = ipText.isNotBlank() && portText.isNotBlank(),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Text("確定")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "目前套用: $appliedServer",
                        color = AppColors.TextGray300,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        ) {
            ViveHeaderSurface(
                isConnected = states.isConnected,
                connectButtonText = states.textConnectButton,
                onConnectClick = { vm.onEvent(CameraEvent.ConnectClicked) },
                onMenuClick = { scope.launch { drawerState.open() } },
                selectedDevice = states.deviceSource,
                onDeviceSourceChange = { vm.onEvent(CameraEvent.DeviceSourceChanged(it)) }
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
