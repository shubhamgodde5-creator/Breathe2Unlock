package com.breathe2unlock.breathing

import android.util.Size
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetectorOptions
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

@Composable
fun CameraBreathPreview(
    onMetric: (Float) -> Unit,
    onStatus: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val latestOnMetric by rememberUpdatedState(onMetric)
    val latestOnStatus by rememberUpdatedState(onStatus)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraExecutor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val detector = PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build()
        )
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 360))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                var lastFrameAt = 0L
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastFrameAt < 200L) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    lastFrameAt = now

                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    try {
                        val input = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        detector.process(input)
                            .addOnSuccessListener(mainExecutor) { pose ->
                                val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
                                val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
                                val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
                                val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)

                                if (leftShoulder != null && rightShoulder != null &&
                                    leftHip != null && rightHip != null
                                ) {
                                    val shoulderWidth = hypot(
                                        (leftShoulder.position.x - rightShoulder.position.x).toDouble(),
                                        (leftShoulder.position.y - rightShoulder.position.y).toDouble()
                                    ).toFloat()
                                    val shoulderMidY = (leftShoulder.position.y + rightShoulder.position.y) / 2f
                                    val hipMidY = (leftHip.position.y + rightHip.position.y) / 2f
                                    val torsoLength = abs(hipMidY - shoulderMidY).coerceAtLeast(1f)
                                    val metric = shoulderWidth / torsoLength
                                    latestOnMetric(metric)
                                    latestOnStatus("Tracking upper-body movement")
                                } else {
                                    latestOnStatus("Keep shoulders and upper torso visible")
                                }
                            }
                            .addOnFailureListener(mainExecutor) {
                                latestOnStatus("Pose tracking is having trouble. Adjust the camera position.")
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } catch (_: Exception) {
                        imageProxy.close()
                    }
                }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
                latestOnStatus("Camera ready — position your upper body in view")
            } catch (e: Exception) {
                latestOnStatus(e.message ?: "Could not start the front camera")
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            try { cameraProvider?.unbindAll() } catch (_: Exception) { }
            try { detector.close() } catch (_: Exception) { }
            cameraExecutor.shutdown()
        }
    }

    Column(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )
        Text(
            text = "Live camera analysis stays on this device. No video is saved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
