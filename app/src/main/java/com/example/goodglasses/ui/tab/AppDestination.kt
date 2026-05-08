package com.example.goodglasses.ui.tab

import com.example.goodglasses.R

sealed class AppDestination(
    val route: String,
    val label: String,
    val iconRes: Int
) {
    data object Glasses : AppDestination("glasses", "Glasses", R.drawable.glasses_solid_full)
    data object Camera : AppDestination("camera", "Camera", R.drawable.camera_solid_full)

    companion object {
        val items = listOf(Glasses, Camera)
    }
}
