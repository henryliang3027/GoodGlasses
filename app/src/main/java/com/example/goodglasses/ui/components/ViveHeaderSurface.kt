package com.example.goodglasses.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goodglasses.R
import com.example.goodglasses.ui.theme.AppColors

enum class DeviceSource {
    HTC, META
}

@Preview
@Composable
fun ViveHeaderSurface(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    isConnected: Boolean? = null,
    connectButtonText: String? = null,
    onConnectClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    selectedDevice: DeviceSource = DeviceSource.HTC,
    onDeviceSourceChange: ((DeviceSource) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    CustomSurface() {
        if (isLandscape) {
            // No inline header row here — its height would eat into the preview/results
            // area. The same controls are drawn as floating pills over the content instead.
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                    content = content
                )
                FloatingHeader(
                    isConnected,
                    connectButtonText,
                    onConnectClick,
                    onMenuClick,
                    selectedDevice,
                    onDeviceSourceChange
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Header(
                    isConnected,
                    connectButtonText,
                    onConnectClick,
                    onMenuClick,
                    selectedDevice,
                    onDeviceSourceChange
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                    content = content
                )
            }
        }
    }
}

@Composable
fun CustomSurface(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppColors.BgDarkPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    // top: status bar / notch. horizontal+bottom: nav bar, whether it renders
                    // as a bottom gesture bar (portrait) or a side rail (landscape).
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
                content = content
            )
        }
    }
}

@Composable
private fun Header(
    isConnected: Boolean? = null,
    connectButtonText: String? = null,
    onConnectClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    selectedDevice: DeviceSource = DeviceSource.HTC,
    onDeviceSourceChange: ((DeviceSource) -> Unit)? = null,
) {
    var currentDevice by rememberSaveable { mutableStateOf(selectedDevice) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 25.dp, end = 25.dp)
            .height(30.dp)
    ) {
        if (onMenuClick != null) {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = AppColors.TextWhite
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        DeviceSourceToggle(
            selected = currentDevice,
            onSelectedChange = {
                currentDevice = it
                onDeviceSourceChange?.invoke(it)
            }
        )
        Spacer(modifier = Modifier.width(10.dp))
        if (onConnectClick != null) {
            IconButton(onClick = onConnectClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.power_off_solid_full),
                    contentDescription = connectButtonText,
                    tint = if (isConnected == true) AppColors.TextRed500 else AppColors.TextBlue400,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (isConnected != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.circle_solid_full),
                contentDescription = null,
                tint = if (isConnected) AppColors.TextGreen400 else AppColors.TextRed500,
                modifier = Modifier.size(12.dp)
            )
//            Spacer(modifier = Modifier.width(5.dp))
//            Text(
//                text = if (isConnected) "Connected" else "Disconnected",
//                color = if (isConnected) AppColors.TextGreen400 else AppColors.TextRed500,
//                fontSize = 14.sp
//            )
        }

    }

    Spacer(modifier = Modifier.height(10.dp))
    HorizontalDivider(
        thickness = 1.dp, color = AppColors.BorderGray700_50,
        modifier = Modifier.padding(top = 0.dp, start = 0.dp, end = 0.dp, bottom = 10.dp)
    )
}

/**
 * Landscape-only replacement for [Header]: same controls, but drawn as floating pills over
 * the content (top-start menu, top-end device toggle/connect/status) instead of a reserved
 * full-width row, so the preview and results panels can extend all the way to the top.
 */
@Composable
private fun BoxScope.FloatingHeader(
    isConnected: Boolean? = null,
    connectButtonText: String? = null,
    onConnectClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    selectedDevice: DeviceSource = DeviceSource.HTC,
    onDeviceSourceChange: ((DeviceSource) -> Unit)? = null,
) {
    var currentDevice by rememberSaveable { mutableStateOf(selectedDevice) }

    if (onMenuClick != null) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.BgGray900_90)
        ) {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = AppColors.TextWhite
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.BgGray900_90)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceSourceToggle(
            selected = currentDevice,
            onSelectedChange = {
                currentDevice = it
                onDeviceSourceChange?.invoke(it)
            }
        )
        if (onConnectClick != null) {
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = onConnectClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.power_off_solid_full),
                    contentDescription = connectButtonText,
                    tint = if (isConnected == true) AppColors.TextRed500 else AppColors.TextBlue400,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (isConnected != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(R.drawable.circle_solid_full),
                contentDescription = null,
                tint = if (isConnected) AppColors.TextGreen400 else AppColors.TextRed500,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

@Composable
private fun DeviceSourceToggle(
    selected: DeviceSource,
    onSelectedChange: (DeviceSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(26.dp)
            .background(AppColors.BgGray800, RoundedCornerShape(13.dp))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceSource.entries.forEach { source ->
            val isSelected = source == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) AppColors.BgBlue600 else Color.Transparent)
                    .clickable(enabled = !isSelected) { onSelectedChange(source) }
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = source.name,
                    color = if (isSelected) AppColors.TextWhite else AppColors.TextGray400,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
