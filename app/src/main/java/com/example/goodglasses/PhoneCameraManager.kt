package com.example.goodglasses

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.goodglasses.util.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

const val PHONE_CAMERA_TAG: String = "PhoneCameraManager"

/**
 * Headless (no preview UI) capture path used when the volume-key shutter
 * fires but neither HTC nor Meta glasses are connected.
 */
class PhoneCameraManager(
    private val activity: AppCompatActivity,
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
    private val requestCameraPermission: () -> Unit,
) {
    private val log = Logger.instance

    private val _imageReceived = MutableSharedFlow<Bitmap>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val imageReceived: SharedFlow<Bitmap> = _imageReceived

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var pendingCapture = false

    fun captureImage() {
        if (!hasCameraPermission()) {
            log.d(PHONE_CAMERA_TAG, "Camera permission not granted, requesting")
            pendingCapture = true
            requestCameraPermission()
            return
        }

        val capture = imageCapture
        if (capture != null) {
            takePicture(capture)
        } else {
            pendingCapture = true
            bindCamera()
        }
    }

    fun onPermissionGranted() {
        if (pendingCapture) captureImage()
    }

    fun cleanup() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            try {
                val provider = future.get()
                val capture = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()
                provider.unbindAll()
                provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, capture)
                cameraProvider = provider
                imageCapture = capture

                if (pendingCapture) takePicture(capture)
            } catch (e: Exception) {
                log.e(PHONE_CAMERA_TAG, "bindCamera failed: ${e.message}")
                pendingCapture = false
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun takePicture(capture: ImageCapture) {
        pendingCapture = false
        val outputStream = ByteArrayOutputStream()
        val options = ImageCapture.OutputFileOptions.Builder(outputStream).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(activity),
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
                    log.d(PHONE_CAMERA_TAG, "Phone camera captured: width=${bitmap.width}, height=${bitmap.height}")
                    _imageReceived.tryEmit(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    log.e(PHONE_CAMERA_TAG, "Capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
