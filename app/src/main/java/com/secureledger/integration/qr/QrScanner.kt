package com.secureledger.integration.qr

import com.journeyapps.barcodescanner.ScanOptions

/** All QR entry points share the same portrait camera / photo-picker screen. */
object QrScanner {
    fun options(prompt: String): ScanOptions = ScanOptions()
        .setCaptureActivity(PortraitQrScannerActivity::class.java)
        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        .setPrompt(prompt)
        .setBeepEnabled(false)
}
