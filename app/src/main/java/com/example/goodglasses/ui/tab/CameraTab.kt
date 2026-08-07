package com.example.goodglasses.ui.tab

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.goodglasses.ui.components.CustomText
import com.example.goodglasses.ui.components.CustomButton
import com.example.goodglasses.ui.components.DeviceSource
import com.example.goodglasses.R.drawable
import com.example.goodglasses.data.isExpiryFailing
import com.example.goodglasses.ui.theme.AppColors

@Composable
fun CameraTab(
    onEvent: (CameraEvent) -> Unit,
    vm: CameraTabModel,
    onOpenPhoneCamera: () -> Unit = {}
) {
    val states = vm.uiState.collectAsStateWithLifecycle()
    val bmp by vm.latestImageReceived.collectAsState()
    val expiryWarningMonths by vm.expiryWarningMonths.collectAsStateWithLifecycle()

    val spacerHeight = 30.dp

    var fractionVideoPreview by remember { mutableStateOf(0f) }
    val fractionImgPreview = if (bmp != null && !states.value.isVideoRecording) 1f else 0f

    Spacer(modifier = Modifier.height(20.dp))
    Column(
        modifier = Modifier
            .padding(start = 25.dp, end = 25.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
//        CustomTitleWithIcon(
//            drawable.camera_solid_full,
//            iconDes = "",
//            iconColor = AppColors.BgIndigo600,
//            iconSize = 25,
//            titleText = "Take Photo: ",
//            fontSize = 16,
//            fontWeight = FontWeight.Normal
//        )
//        Spacer(modifier = Modifier.height(10.dp))
//        CustomButton(
//            "Take Photo",
//            color = AppColors.BgIndigo600,
//            height = 50,
//            onClick = {
//                onEvent(CameraEvent.TakePhotoClicked)
//            })
//        Spacer(modifier = Modifier.height(spacerHeight))

        //--------------------------------------------------

//        CustomTitleWithIcon(
//            drawable.video_solid_full,
//            iconDes = "",
//            iconColor = AppColors.TextRed400,
//            iconSize = 25,
//            titleText = "Video Recording: ",
//            fontSize = 16,
//            fontWeight = FontWeight.Normal
//        )
//        Spacer(modifier = Modifier.height(10.dp))
//        CustomButton(
//            text = if (!states.value.isVideoRecording) "Start Recording" else "Stop Recording",
//            color = if (!states.value.isVideoRecording) AppColors.BgRed600 else AppColors.BgDarkSecondary,
//            height = 50,
//            enable = true,
//            onClick = {
//                if (!states.value.isVideoRecording)
//                    onEvent(CameraEvent.StartRecordingClicked)
//                else
//                    onEvent(CameraEvent.StopRecordingClicked)
//            }
//        )
//        Spacer(modifier = Modifier.height(spacerHeight))

        //--------------------------------------------------

        Spacer(modifier = Modifier.height(10.dp))

        if (states.value.deviceSource == DeviceSource.META && states.value.isPairing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = AppColors.TextBlue400
                )
                Spacer(modifier = Modifier.width(8.dp))
                CustomText(
                    "等待眼鏡配對中...",
                    color = AppColors.TextGray300
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp.dp - 50.dp // Column padding each side 25
        val targetHeight = minOf((screenWidthDp / states.value.previewRatio), 300.dp)
        val targetWidth = targetHeight * states.value.previewRatio

        Box(
            modifier = Modifier
                .width(targetWidth)
                .height(targetHeight)
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color = AppColors.BgGray800)
                .dashedBorder(
                    color = AppColors.BorderGray600,
                    strokeWidth = 3.dp,
                    cornerRadius = 10.dp,
                    intervals = floatArrayOf(15f, 15f)
                )
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            fractionVideoPreview = if (!states.value.isVideoRecording) 0f else 1f

            if (fractionImgPreview == 0f && fractionVideoPreview == 0f) {
                CustomText(
                    "照片顯示區",
                    color = AppColors.TextGray500
                )
            }

            StreamPreview(
                modifier = Modifier
                    .padding(2.dp)
                    .fillMaxSize(fractionVideoPreview)
                    .clip(RoundedCornerShape(10.dp)),
                onSurfaceReadyCallback = { surface ->
                    vm.attachSurface(surface)
                },
                onSurfaceDestroyed = {
                    vm.detachSurface()
                }
            )

            bmp?.let {
                Image(
                    modifier = Modifier
                        .fillMaxSize(fractionImgPreview),
                    bitmap = it.asImageBitmap(),
                    contentDescription = null
                )
            }

            if (fractionImgPreview == 1f && states.value.expiryItems.isNotEmpty()) {
                val imgW = bmp!!.width.toFloat()
                val imgH = bmp!!.height.toFloat()
                val boxColors = listOf(
                    Color(0xFF4ADE80), // 綠
                    Color(0xFF60A5FA), // 藍
                    Color(0xFFFB923C), // 橘
                    Color(0xFFF472B6), // 粉
                    Color(0xFFA78BFA), // 紫
                    Color(0xFFFACC15), // 黃
                )
                val dateColor = Color(0xFFFBBF24)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // ContentScale.Fit centers the image — compute actual displayed area
                    val imgAspect = imgW / imgH
                    val canvasAspect = size.width / size.height
                    val dispW: Float
                    val dispH: Float
                    val offsetX: Float
                    val offsetY: Float
                    if (imgAspect > canvasAspect) {
                        // letterboxed: image wider than canvas → bars top/bottom
                        dispW = size.width
                        dispH = size.width / imgAspect
                        offsetX = 0f
                        offsetY = (size.height - dispH) / 2f
                    } else {
                        // pillarboxed: image taller than canvas → bars left/right
                        dispH = size.height
                        dispW = size.height * imgAspect
                        offsetX = (size.width - dispW) / 2f
                        offsetY = 0f
                    }
                    val scaleX = dispW / imgW
                    val scaleY = dispH / imgH

                    states.value.expiryItems.forEachIndexed { index, item ->
                        val color = boxColors[index % boxColors.size]
                        val (boxTopLeft, boxSize) = bboxToRect(item.bbox, scaleX, scaleY, offsetX, offsetY)
                        drawRect(color = color, topLeft = boxTopLeft, size = boxSize, style = Stroke(width = 3.dp.toPx()))
                        drawBboxLabel(item.name, boxTopLeft, color)

                        item.dateBbox?.let { dateBbox ->
                            val (dateTopLeft, dateSize) = bboxToRect(dateBbox, scaleX, scaleY, offsetX, offsetY)
                            drawRect(color = dateColor, topLeft = dateTopLeft, size = dateSize, style = Stroke(width = 2.dp.toPx()))
                            item.dateStr?.let { dateStr -> drawBboxLabel(dateStr, dateTopLeft, dateColor) }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacerHeight))
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.BorderGray700_50)
        Spacer(modifier = Modifier.height(spacerHeight))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.BgDarkSecondary)
                .padding(16.dp)
        ) {
            when {
                states.value.isAnalyzing -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = AppColors.TextBlue400
                    )
                }
                states.value.analysisError != null -> CustomText(
                    "Error: ${states.value.analysisError}",
                    color = AppColors.TextRed400
                )
                states.value.inventoryItems.isNotEmpty() -> {
                    states.value.inventoryItems.forEachIndexed { index, item ->
                        val positionLabel = if (item.position == "top") "上層" else "下層"
                        Text(
                            text = "$positionLabel 缺貨",
                            color = AppColors.TextBlue400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp, bottom = 4.dp)
                        )
                        item.outOfStock.forEach { name ->
                            Text(
                                text = "• $name",
                                color = AppColors.TextWhite,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                            )
                        }
                        if (index < states.value.inventoryItems.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 8.dp),
                                thickness = 0.5.dp,
                                color = AppColors.BorderGray700_50
                            )
                        }
                    }
                }
                states.value.expiryItems.isNotEmpty() -> {
                    val failingItems = states.value.expiryItems.filter { it.isExpiryFailing(expiryWarningMonths) }
                    if (failingItems.isNotEmpty()) {
                        Text(
                            text = "效期未合格 (${failingItems.size})",
                            color = AppColors.TextRed400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        failingItems.forEach { item ->
                            Text(
                                text = "• ${item.name} ${item.dateStr ?: "未提供"}",
                                color = AppColors.TextRed400,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                            thickness = 0.5.dp,
                            color = AppColors.BorderGray700_50
                        )
                    }
                    Text(
                        text = "共 ${states.value.expiryItems.size} 項",
                        color = AppColors.TextGray300,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 10.dp),
                        thickness = 0.5.dp,
                        color = AppColors.BorderGray700_50
                    )
                    states.value.expiryItems.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (index == 0) 0.dp else 6.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.name,
                                color = AppColors.TextWhite,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = item.dateStr ?: "未提供",
                                color = if (item.isExpiryFailing(expiryWarningMonths)) AppColors.TextRed400 else AppColors.TextBlue400,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (index < states.value.expiryItems.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 6.dp),
                                thickness = 0.5.dp,
                                color = AppColors.BorderGray700_50
                            )
                        }
                    }
                }
                else -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CustomText(
                        if (states.value.hasAnalyzed) {
                            if (states.value.analysisMode == AnalysisMode.INVENTORY) "目前無缺貨商品" else "目前無效期資料"
                        } else "",
                        color = AppColors.TextGray500
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacerHeight))
        CustomButton(
            "手機拍照",
            color = AppColors.BgIndigo600,
            height = 50,
            onClick = onOpenPhoneCamera
        )
        if (states.value.deviceSource == DeviceSource.META && states.value.isConnected) {
            Spacer(modifier = Modifier.height(12.dp))
            CustomButton(
                "META 拍照",
                color = AppColors.BgIndigo600,
                height = 50,
                enable = !states.value.isImageCapturing,
                onClick = { onEvent(CameraEvent.TakePhotoClicked) }
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

/** 將原圖座標的 [x1, y1, x2, y2] bbox 換算成畫布上實際顯示的 topLeft/size */
private fun bboxToRect(
    bbox: List<Double>,
    scaleX: Float,
    scaleY: Float,
    offsetX: Float,
    offsetY: Float
): Pair<Offset, Size> {
    val topLeft = Offset(
        bbox[0].toFloat() * scaleX + offsetX,
        bbox[1].toFloat() * scaleY + offsetY
    )
    val rectSize = Size(
        (bbox[2] - bbox[0]).toFloat() * scaleX,
        (bbox[3] - bbox[1]).toFloat() * scaleY
    )
    return topLeft to rectSize
}

/** 在 bbox 左上角畫出帶底色的文字標籤（框名稱 / 日期字串） */
private fun DrawScope.drawBboxLabel(text: String, boxTopLeft: Offset, bgColor: Color) {
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 28f
        isAntiAlias = true
    }
    val textWidth = textPaint.measureText(text)
    val bgTop = (boxTopLeft.y - 32f).coerceAtLeast(0f)
    drawRect(
        color = bgColor,
        topLeft = Offset(boxTopLeft.x, bgTop),
        size = Size(textWidth + 12f, 32f)
    )
    drawContext.canvas.nativeCanvas.drawText(
        text,
        boxTopLeft.x + 6f,
        bgTop + 24f,
        textPaint
    )
}

fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp = 1.dp,
    cornerRadius: Dp = 0.dp,
    intervals: FloatArray = floatArrayOf(10f, 10f)
) = this.then(
    Modifier.drawBehind {
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(intervals, 0f)
        )

        val width = size.width
        val height = size.height

        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(width, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                cornerRadius.toPx(),
                cornerRadius.toPx()
            ),
            style = stroke
        )
    }
)

@Composable
fun ModeToggle(
    selectedMode: AnalysisMode,
    onModeSelected: (AnalysisMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(25.dp))
            .background(AppColors.BgDarkSecondary)
            .padding(4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        listOf(AnalysisMode.INVENTORY to "缺貨", AnalysisMode.EXPIRY to "效期").forEach { (mode, label) ->
            val isSelected = selectedMode == mode
            Button(
                onClick = { onModeSelected(mode) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) AppColors.BgBlue600 else Color.Transparent,
                    contentColor = if (isSelected) Color.White else AppColors.TextGray400,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = AppColors.TextGray400
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StreamPreview(
    modifier: Modifier,
    onSurfaceReadyCallback: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    val onReady by rememberUpdatedState(onSurfaceReadyCallback)
    val onDestroyed by rememberUpdatedState(onSurfaceDestroyed)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                var surface: Surface? = null
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        surface = Surface(st)
                        onReady(surface!!)
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        onDestroyed()
                        surface?.release()
                        surface = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        }
    )
}
