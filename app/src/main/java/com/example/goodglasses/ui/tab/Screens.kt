package com.example.goodglasses.ui.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.goodglasses.ui.components.ViveHeaderSurface
import com.example.goodglasses.ViveGlassKitManager

@Composable
fun GlassesScreen(clientManager: ViveGlassKitManager) {
    val vm = remember(clientManager) { GlassTabModel(clientManager) }
    ViveHeaderSurface {
        GlassTab(vm::onEvent, vm)
    }
}

@Composable
fun CameraScreen(clientManager: ViveGlassKitManager) {
    val vm = remember(clientManager) { CameraTabModel(clientManager) }
    ViveHeaderSurface {
        CameraTab(vm::onEvent, vm)
    }
}
