package com.secureledger.integration.qr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.secureledger.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Returns ScanContract-compatible results without changing invitation/sync/AA validation. */
class PortraitQrScannerActivity : ComponentActivity() {
    private lateinit var scanner: DecoratedBarcodeView
    private lateinit var status: TextView
    private lateinit var gallery: Button
    private lateinit var cameraButton: Button
    private lateinit var progress: View
    private var resumed = false
    private var selectingImage = false
    private var decodingImage = false
    private var permissionRequested = false
    private var cameraFailed = false
    private var completed = false
    private var pendingImage: Uri? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        showCameraAvailability(granted)
        if (granted) resumeCamera()
    }
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectingImage = false
        if (uri == null) resumeCamera() else decodeImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContentView(R.layout.activity_qr_scanner)
        val root = findViewById<View>(R.id.qr_scanner_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        scanner = findViewById(R.id.qr_camera)
        status = findViewById(R.id.qr_status)
        gallery = findViewById(R.id.qr_gallery)
        cameraButton = findViewById(R.id.qr_camera_permission)
        progress = findViewById(R.id.qr_progress)
        selectingImage = savedInstanceState?.getBoolean("selectingImage") ?: false
        permissionRequested = savedInstanceState?.getBoolean("permissionRequested") ?: false
        pendingImage = savedInstanceState?.getString("pendingImage")?.let(Uri::parse)
        findViewById<TextView>(R.id.qr_prompt).text = intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE)
            ?: getString(R.string.qr_scan_prompt)
        scanner.setStatusText("")
        scanner.barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE), null, "UTF-8", 2)
        scanner.barcodeView.addStateListener(object : CameraPreview.StateListener {
            override fun previewSized() = Unit
            override fun previewStarted() = Unit
            override fun previewStopped() = Unit
            override fun cameraClosed() = Unit
            override fun cameraError(error: Exception) {
                cameraFailed = true
                cameraButton.visibility = View.VISIBLE
                cameraButton.setText(R.string.qr_retry_camera)
                status.setText(R.string.qr_camera_unavailable)
            }
        })
        findViewById<View>(R.id.qr_back).setOnClickListener { finish() }
        gallery.setOnClickListener {
            selectingImage = true
            pauseCamera()
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        cameraButton.setOnClickListener {
            cameraFailed = false
            if (hasCameraPermission()) {
                showCameraAvailability(true)
                resumeCamera()
            } else {
                permissionRequested = true
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        showCameraAvailability(hasCameraPermission())
        pendingImage?.let(::decodeImage)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!hasCameraPermission() && !permissionRequested && !selectingImage && !decodingImage) {
            permissionRequested = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        } else resumeCamera()
    }

    override fun onPause() {
        resumed = false
        pauseCamera()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("selectingImage", selectingImage)
        outState.putBoolean("permissionRequested", permissionRequested)
        outState.putString("pendingImage", pendingImage?.toString())
        super.onSaveInstanceState(outState)
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun showCameraAvailability(granted: Boolean) {
        cameraButton.visibility = if (granted) View.GONE else View.VISIBLE
        cameraButton.setText(R.string.qr_enable_camera)
        status.setText(if (granted) R.string.qr_scan_hint else R.string.qr_camera_permission_hint)
    }

    private fun resumeCamera() {
        if (!resumed || completed || selectingImage || decodingImage || cameraFailed || !hasCameraPermission()) return
        if (scanner.barcodeView.cameraInstance == null) scanner.resume()
        scanner.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                if (!selectingImage && !decodingImage) returnQr(result.text)
            }
            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) = Unit
        })
    }

    private fun pauseCamera() {
        scanner.barcodeView.stopDecoding()
        scanner.pause()
    }

    private fun decodeImage(uri: Uri) {
        pauseCamera()
        pendingImage = uri
        decodingImage = true
        gallery.isEnabled = false
        cameraButton.isEnabled = false
        progress.visibility = View.VISIBLE
        status.setText(R.string.qr_decoding_image)
        lifecycleScope.launch {
            try {
                val contents = withContext(Dispatchers.IO) { QrImageDecoder.decode(contentResolver, uri) }
                if (contents != null) returnQr(contents) else status.setText(R.string.qr_image_no_code)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status.setText(R.string.qr_image_unreadable)
            } finally {
                pendingImage = null
                decodingImage = false
                gallery.isEnabled = true
                cameraButton.isEnabled = true
                progress.visibility = View.GONE
                if (!completed) resumeCamera()
            }
        }
    }

    private fun returnQr(contents: String) {
        if (completed) return
        completed = true
        pauseCamera()
        setResult(Activity.RESULT_OK, Intent()
            .putExtra(Intents.Scan.RESULT, contents)
            .putExtra(Intents.Scan.RESULT_FORMAT, BarcodeFormat.QR_CODE.name))
        finish()
    }
}
