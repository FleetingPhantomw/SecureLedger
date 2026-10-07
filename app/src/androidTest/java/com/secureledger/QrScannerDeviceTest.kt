package com.secureledger

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.ScanContract
import com.secureledger.integration.qr.PortraitQrScannerActivity
import com.secureledger.integration.qr.QrScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One focused device case: real scanner UI and URI decoding, controlled photo-picker result. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class QrScannerDeviceTest {
    @Test(timeout = 45_000)
    fun portraitScannerReturnsQrFromSelectedImage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val text = "SecureLedger portrait gallery QR test"
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 600, 600)
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).apply {
            val pixels = IntArray(600 * 600) { index ->
                if (matrix[index % 600, index / 600]) Color.BLACK else Color.WHITE
            }
            setPixels(pixels, 0, 600, 0, 0, 600, 600)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "secureledger-qr-test-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SecureLedgerTest")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        val pickerIntent = ActivityResultContracts.PickVisualMedia().createIntent(
            context, PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
        val pickerMonitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != pickerIntent.action) return null
                assertEquals("Picker must request images only", "image/*", intent.type)
                return Instrumentation.ActivityResult(Activity.RESULT_OK,
                    Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            assertNotNull("System photo picker / document fallback must be available", pickerIntent.resolveActivity(context.packageManager))
            instrumentation.addMonitor(pickerMonitor)
            val intent = ScanContract().createIntent(context, QrScanner.options("扫描测试二维码"))
            ActivityScenario.launchActivityForResult<PortraitQrScannerActivity>(intent).use { scenario ->
                onView(withId(R.id.qr_gallery)).check(matches(isDisplayed()))
                val previewReady = CountDownLatch(1)
                val scannerDestroyed = CountDownLatch(1)
                scenario.onActivity { activity ->
                    activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_DESTROY) scannerDestroyed.countDown()
                    })
                    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
                    assertEquals(Configuration.ORIENTATION_PORTRAIT, activity.resources.configuration.orientation)
                    val root = activity.findViewById<android.view.View>(R.id.qr_scanner_root)
                    assertTrue("Portrait content must be taller than wide", root.height > root.width)
                    val camera = activity.findViewById<DecoratedBarcodeView>(R.id.qr_camera).barcodeView
                    if (camera.isPreviewActive) previewReady.countDown()
                    else camera.addStateListener(object : CameraPreview.StateListener {
                        override fun previewStarted() { previewReady.countDown() }
                        override fun previewSized() = Unit
                        override fun previewStopped() = Unit
                        override fun cameraClosed() = Unit
                        override fun cameraError(error: Exception) = Unit
                    })
                }
                assertTrue("Camera preview must start", previewReady.await(10, TimeUnit.SECONDS))
                scenario.onActivity { activity ->
                    val camera = activity.findViewById<DecoratedBarcodeView>(R.id.qr_camera).barcodeView
                    assertTrue(camera.isPreviewActive)
                    val frame = requireNotNull(camera.framingRect)
                    assertTrue("QR frame must be square", kotlin.math.abs(frame.width() - frame.height()) <= 2)
                }
                // Preserve the UI of this same run for visual QA, without launching a second test.
                instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                    File(context.cacheDir, "qr-scanner-portrait.png").outputStream().use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    screenshot.recycle()
                }
                onView(withId(R.id.qr_gallery)).perform(click())
                val result = scenario.result
                assertEquals(Activity.RESULT_OK, result.resultCode)
                assertEquals(text, ScanContract().parseResult(result.resultCode, result.resultData).contents)
                assertEquals("Only one selected-image request", 1, pickerMonitor.hits)
                // The result can arrive before ON_DESTROY. Avoid asking ActivityScenario to
                // finish the already-finishing activity and racing its helper EmptyActivity.
                assertTrue("Scanner must finish after returning the QR", scannerDestroyed.await(10, TimeUnit.SECONDS))
            }
        } finally {
            instrumentation.removeMonitor(pickerMonitor)
            bitmap.recycle()
            resolver.delete(uri, null, null)
        }
    }
}
