package com.example.goodglasses.ui.tab

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
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
    val states by vm.uiState.collectAsStateWithLifecycle()
    val bmp by vm.latestImageReceived.collectAsState()
    val expiryWarningMonths by vm.expiryWarningMonths.collectAsStateWithLifecycle()

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        CameraTabLandscape(states, bmp, expiryWarningMonths, onEvent, vm, onOpenPhoneCamera)
    } else {
        CameraTabPortrait(states, bmp, expiryWarningMonths, onEvent, vm, onOpenPhoneCamera)
    }
}

@Composable
private fun CameraTabPortrait(
    states: CameraUiState,
    bmp: Bitmap?,
    expiryWarningMonths: Long,
    onEvent: (CameraEvent) -> Unit,
    vm: CameraTabModel,
    onOpenPhoneCamera: () -> Unit
) {
    val spacerHeight = 30.dp

    Spacer(modifier = Modifier.height(20.dp))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(start = 25.dp, end = 25.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        if (states.deviceSource == DeviceSource.META && states.isPairing) {
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
        val targetHeight = minOf((screenWidthDp / states.previewRatio), 300.dp)
        val targetWidth = targetHeight * states.previewRatio

        MediaPreviewBox(
            states = states,
            bmp = bmp,
            vm = vm,
            modifier = Modifier
                .width(targetWidth)
                .height(targetHeight)
                .padding(top = 8.dp)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(spacerHeight))
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.BorderGray700_50)
        Spacer(modifier = Modifier.height(spacerHeight))

        ResultsPanel(
            states = states,
            bmp = bmp,
            expiryWarningMonths = expiryWarningMonths,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        )

        Spacer(modifier = Modifier.height(spacerHeight))
        CustomButton(
            "手機拍照",
            color = AppColors.BgIndigo600,
            height = 50,
            onClick = onOpenPhoneCamera
        )
        if (states.deviceSource == DeviceSource.META && states.isConnected) {
            Spacer(modifier = Modifier.height(12.dp))
            CustomButton(
                "META 拍照",
                color = AppColors.BgIndigo600,
                height = 50,
                enable = !states.isImageCapturing,
                onClick = { onEvent(CameraEvent.TakePhotoClicked) }
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Landscape layout: preview/canvas box + capture buttons on the left,
 * an independently scrollable results list on the right.
 */
@Composable
private fun CameraTabLandscape(
    states: CameraUiState,
    bmp: Bitmap?,
    expiryWarningMonths: Long,
    onEvent: (CameraEvent) -> Unit,
    vm: CameraTabModel,
    onOpenPhoneCamera: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (states.deviceSource == DeviceSource.META && states.isPairing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.TextBlue400
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CustomText(
                        "等待眼鏡配對中...",
                        color = AppColors.TextGray300,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                MediaPreviewBox(
                    states = states,
                    bmp = bmp,
                    vm = vm,
                    modifier = Modifier.fillMaxSize()
                )

                // Capture buttons float over the preview's bottom-right corner instead of
                // taking their own row, so the preview gets the full column height.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (states.deviceSource == DeviceSource.META && states.isConnected) {
                        CaptureIconButton(
                            icon = drawable.glasses_solid_full,
                            label = "META 拍照",
                            enabled = !states.isImageCapturing,
                            onClick = { onEvent(CameraEvent.TakePhotoClicked) }
                        )
                    }
                    CaptureIconButton(
                        icon = drawable.camera_solid_full,
                        label = "手機拍照",
                        showLabel = false,
                        onClick = onOpenPhoneCamera
                    )
                }
            }
        }

        ResultsPanel(
            states = states,
            bmp = bmp,
            expiryWarningMonths = expiryWarningMonths,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            scrollable = true
        )
    }
}

/** Live video/photo preview box with the effective-date bbox overlay. Shared by both orientations. */
@Composable
private fun MediaPreviewBox(
    states: CameraUiState,
    bmp: Bitmap?,
    vm: CameraTabModel,
    modifier: Modifier = Modifier
) {
    var fractionVideoPreview by remember { mutableStateOf(0f) }
    fractionVideoPreview = if (!states.isVideoRecording) 0f else 1f
    val fractionImgPreview = if (bmp != null && !states.isVideoRecording) 1f else 0f

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color = AppColors.BgGray800)
            .dashedBorder(
                color = AppColors.BorderGray600,
                strokeWidth = 3.dp,
                cornerRadius = 10.dp,
                intervals = floatArrayOf(15f, 15f)
            ),
        contentAlignment = Alignment.Center
    ) {
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

        if (fractionImgPreview == 1f && states.expiryItems.isNotEmpty()) {
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

                states.expiryItems.forEachIndexed { index, item ->
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
}

/** Inventory / expiry results list. Shared by both orientations; `scrollable` gives it its own scroll region. */
@Composable
private fun ResultsPanel(
    states: CameraUiState,
    bmp: Bitmap?,
    expiryWarningMonths: Long,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.BgDarkSecondary)
            .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
            .padding(16.dp)
    ) {
        when {
            states.isAnalyzing -> Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = AppColors.TextBlue400
                )
            }
            states.analysisError != null -> CustomText(
                "Error: ${states.analysisError}",
                color = AppColors.TextRed400
            )
            states.inventoryItems.isNotEmpty() -> {
                states.inventoryItems.forEachIndexed { index, item ->
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
                    if (index < states.inventoryItems.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 8.dp),
                            thickness = 0.5.dp,
                            color = AppColors.BorderGray700_50
                        )
                    }
                }
            }
            states.expiryItems.isNotEmpty() -> {
                val failingItems = states.expiryItems.filter { it.isExpiryFailing(expiryWarningMonths) }
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
                    text = "共 ${states.expiryItems.size} 項",
                    color = AppColors.TextGray300,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 10.dp),
                    thickness = 0.5.dp,
                    color = AppColors.BorderGray700_50
                )
                states.expiryItems.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (index == 0) 0.dp else 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val thumbnail = remember(bmp, item.bbox) { bmp?.let { cropToBbox(it, item.bbox) } }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = item.name,
                                color = AppColors.TextWhite,
                                fontSize = 14.sp
                            )
                            Text(
                                text = item.dateStr ?: "未提供",
                                color = if (item.isExpiryFailing(expiryWarningMonths)) AppColors.TextRed400 else AppColors.TextBlue400,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(80.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(AppColors.BgGray700)
                        ) {
                            thumbnail?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    if (index < states.expiryItems.lastIndex) {
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
                    if (states.hasAnalyzed) {
                        if (states.analysisMode == AnalysisMode.INVENTORY) "目前無缺貨商品" else "目前無效期資料"
                    } else "",
                    color = AppColors.TextGray500
                )
            }
        }
    }
}

/** Compact circular icon button used for capture actions in landscape, where full-width text buttons don't fit. */
@Composable
private fun CaptureIconButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = AppColors.BgIndigo600,
    enabled: Boolean = true,
    showLabel: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (enabled) background else AppColors.BgGray700)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = if (enabled) Color.White else AppColors.TextGray500,
                modifier = Modifier.size(24.dp)
            )
        }
        if (showLabel) {
            Spacer(modifier = Modifier.height(4.dp))
            CustomText(
                label,
                color = AppColors.TextWhite,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.BgGray900_90)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
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

/** 依原圖座標的 [x1, y1, x2, y2] bbox 從來源圖裁切出縮圖，座標會被夾在圖片範圍內 */
private fun cropToBbox(source: Bitmap, bbox: List<Double>): Bitmap? {
    val x1 = bbox[0].toInt().coerceIn(0, source.width - 1)
    val y1 = bbox[1].toInt().coerceIn(0, source.height - 1)
    val x2 = bbox[2].toInt().coerceIn(x1 + 1, source.width)
    val y2 = bbox[3].toInt().coerceIn(y1 + 1, source.height)
    return runCatching { Bitmap.createBitmap(source, x1, y1, x2 - x1, y2 - y1) }.getOrNull()
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
