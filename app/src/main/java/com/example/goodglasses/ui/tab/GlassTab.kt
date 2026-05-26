package com.example.goodglasses.ui.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.goodglasses.ui.components.CustomHorizontalDivider
import com.example.goodglasses.ui.components.CustomText
import com.example.goodglasses.ui.components.CustomTitleWithIcon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.goodglasses.R.drawable
import com.example.goodglasses.ui.components.CustomButton
import com.example.goodglasses.ui.theme.AppColors

@Composable
fun GlassTab(
    onEvent: (GlassEvent) -> Unit,
    vm: GlassTabModel
) {
    val states = vm.uiState.collectAsStateWithLifecycle()
    val connectionStatus = if (states.value.isConnected) "Connected" else "Disconnected"

    val spacerHeight = 30.dp
    val spacerHeightSecond = 10.dp

    Spacer(modifier = Modifier.height(20.dp))

    Column(
        modifier = Modifier
            .padding(start = 25.dp, end = 25.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {

        //--------------------------------------------------

        Spacer(modifier = Modifier.height(spacerHeightSecond))
        CustomTitleWithIcon(
            drawable.glasses_solid_full,
            iconColor = AppColors.TextBlue400,
            iconSize = 25,
            titleText = " Glass Status",
            fontSize = 23
        )
        Spacer(modifier = Modifier.height(spacerHeightSecond))
        CustomHorizontalDivider()
        //--------------------------------------------------

        Spacer(modifier = Modifier.height(spacerHeightSecond))
        Spacer(modifier = Modifier.height(spacerHeight))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start

            ) {
                CustomText(
                    "Connection Status : ",
                    color = AppColors.TextGray300
                )
            }

            CustomTitleWithIcon(
                drawable.circle_solid_full,
                iconDes = "",
                iconSize = 15,
                iconColor = if (!states.value.isConnected) AppColors.TextRed500 else AppColors.TextGreen400,
                titleText = connectionStatus,
                titleColor = if (!states.value.isConnected) AppColors.TextRed500 else AppColors.TextGreen400,
                fontSize = 16,
                arrangement = Arrangement.End
            )
        }
        Spacer(modifier = Modifier.height(spacerHeight))

        CustomButton(
            states.value.textConnectButton,
            color = AppColors.BgBlue600,
            height = 50,
            onClick = {
                onEvent(GlassEvent.ConnectClicked)
            })
        Spacer(modifier = Modifier.height(spacerHeight))
        //--------------------------------------------------
    }
}

