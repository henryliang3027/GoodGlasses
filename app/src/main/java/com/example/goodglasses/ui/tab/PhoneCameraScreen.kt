package com.example.goodglasses.ui.tab

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.goodglasses.R
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Snaps a raw [OrientationEventListener] reading (0..359, increasing clockwise as the device
 * physically rotates) to the nearest of the four cardinal holds. This is independent of the
 * activity/display rotation, which stays locked to landscape for this whole app.
 */
private fun snapToCardinal(orientation: Int): Int = when (orientation) {
    in 45 until 135 -> 270
    in 135 until 225 -> 180
    in 225 until 315 -> 90
    else -> 0
}

/** Maps a cardinal hold (as produced by [snapToCardinal]) to the matching [Surface] rotation constant. */
private fun cardinalToSurfaceRotation(cardinal: Int): Int = when (cardinal) {
    // Empirically (not the textbook Surface.getRotation() direction) 90/270 map straight
    // through on this camera stack — inverting them, as the "official" formula suggests,
    // produced photos rotated 180° (upside down) whenever held in either landscape hold.
    90 -> Surface.ROTATION_90
    180 -> Surface.ROTATION_180
    270 -> Surface.ROTATION_270
    else -> Surface.ROTATION_0
}

/**
 * Tracks how the phone is physically held (0/90/180/270, clockwise) via the accelerometer,
 * independent of the activity's locked display orientation. Like Samsung's camera app, this
 * screen never itself rotates — only the button icons and the captured photo's orientation
 * follow this value.
 */
@Composable
private fun rememberDeviceHoldRotation(): Int {
    val context = LocalContext.current
    var cardinal by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val snapped = snapToCardinal(orientation)
                if (snapped != cardinal) cardinal = snapped
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }
    return cardinal
}

@Composable
fun PhoneCameraScreen(
    onPhotoCaptured: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // The activity display stays landscape-locked at all times; this tracks how the phone is
    // actually being held so the captured photo's EXIF orientation can be corrected even though
    // the on-screen buttons themselves no longer rotate — only reposition — with the hold.
    val deviceHoldRotation = rememberDeviceHoldRotation()
    val isPortrait = deviceHoldRotation == 0 || deviceHoldRotation == 180

    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
    }
    val providerRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    DisposableEffect(Unit) {
        onDispose { providerRef[0]?.unbindAll() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                    }
                    ProcessCameraProvider.getInstance(ctx).also { future ->
                        future.addListener({
                            val provider = future.get()
                            providerRef[0] = provider
                            val preview = Preview.Builder()
                                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                                .build()
                                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                Log.e("PhoneCameraScreen", "Camera bind failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isPortrait) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onDismiss)
                CaptureButton {
                    takePhoto(imageCapture, mainExecutor, deviceHoldRotation, onPhotoCaptured)
                }
            }
        } else {
            BackButton(
                onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 16.dp, start = 16.dp)
            )
            CaptureButton(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 24.dp, start = 24.dp)
            ) {
                takePhoto(imageCapture, mainExecutor, deviceHoldRotation, onPhotoCaptured)
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.ArrowBack,
            contentDescription = "返回",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun CaptureButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(60.dp)
            .background(Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.camera_solid_full),
            contentDescription = "拍照",
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
    }
}

/**
 * Bakes in the orientation the phone is actually being held at right now (rather than whatever
 * the landscape-locked display rotation says) so the captured photo's EXIF orientation, and the
 * pixel data after correcting for it, come out upright regardless of button layout.
 */
private fun takePhoto(
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    deviceHoldRotation: Int,
    onPhotoCaptured: (Bitmap) -> Unit
) {
    imageCapture.targetRotation = cardinalToSurfaceRotation(deviceHoldRotation)

    val outputStream = ByteArrayOutputStream()
    val options = ImageCapture.OutputFileOptions.Builder(outputStream).build()
    imageCapture.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val bytes = outputStream.toByteArray()
                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val orientation = ExifInterface(ByteArrayInputStream(bytes))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val degrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees != 0f) {
                    bitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height,
                        Matrix().apply { postRotate(degrees) }, true
                    )
                }
                Log.d("PhoneCameraScreen", "captured photo: width=${bitmap.width}, height=${bitmap.height}")
                onPhotoCaptured(bitmap)
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("PhoneCameraScreen", "Capture failed: ${exception.message}", exception)
            }
        }
    )
}
