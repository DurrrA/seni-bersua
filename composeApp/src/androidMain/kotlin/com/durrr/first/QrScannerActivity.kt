package com.durrr.first

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlin.math.min

class QrScannerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_RESULT = "qr"
        private const val REQUEST_CAMERA = 1001
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var processed = false
    private lateinit var previewView: PreviewView
    private var camera: Camera? = null
    private var torchEnabled = false
    private lateinit var torchButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)
        val root = FrameLayout(this)
        root.addView(previewView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        root.addView(QrOverlayView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        }

        val cancelButton = Button(this).apply {
            text = "Cancel"
            setOnClickListener { finish() }
        }

        torchButton = Button(this).apply {
            text = "Torch Off"
            setOnClickListener { toggleTorch() }
        }

        controls.addView(cancelButton)
        controls.addView(torchButton)

        root.addView(controls, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP,
        ))

        setContentView(root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            showPermissionDeniedUi()
        }
    }

    private fun showPermissionDeniedUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())
        }

        val message = TextView(this).apply {
            text = "Camera permission is required to scan QR codes."
            gravity = Gravity.CENTER
        }

        val settingsButton = Button(this).apply {
            text = "Open Settings"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }

        val cancelButton = Button(this).apply {
            text = "Cancel"
            setOnClickListener { finish() }
        }

        root.addView(message)
        root.addView(settingsButton)
        root.addView(cancelButton)
        setContentView(root)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analyzer = ImageAnalysis.Builder().build().also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (processed) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        val scanner = BarcodeScanning.getClient()
                        scanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstOrNull()?.rawValue
                                if (!value.isNullOrBlank()) {
                                    processed = true
                                    val resultIntent = intent.apply {
                                        putExtra(EXTRA_RESULT, value)
                                    }
                                    setResult(RESULT_OK, resultIntent)
                                    finish()
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }
            }

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analyzer,
            )

            val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
            torchButton.isEnabled = hasFlash
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleTorch() {
        if (camera?.cameraInfo?.hasFlashUnit() != true) return
        torchEnabled = !torchEnabled
        camera?.cameraControl?.enableTorch(torchEnabled)
        torchButton.text = if (torchEnabled) "Torch On" else "Torch Off"
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}

private class QrOverlayView(context: Context) : View(context) {
    private val overlayPaint = Paint().apply {
        color = 0x88000000.toInt()
        style = Paint.Style.FILL
    }
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val borderPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        val size = min(width, height) * 0.6f
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val rectRight = left + size
        val rectBottom = top + size
        canvas.drawRect(left, top, rectRight, rectBottom, clearPaint)
        canvas.drawRect(left, top, rectRight, rectBottom, borderPaint)
    }
}
