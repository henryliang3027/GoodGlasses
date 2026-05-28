package com.example.goodglasses.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goodglasses.R
import com.example.goodglasses.ui.theme.AppColors

@Preview
@Composable
fun ViveHeaderSurface(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    isConnected: Boolean? = null,
    connectButtonText: String? = null,
    onConnectClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    CustomSurface() {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(isConnected, connectButtonText, onConnectClick, onMenuClick)
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
                content = content
            )
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
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
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
) {
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
