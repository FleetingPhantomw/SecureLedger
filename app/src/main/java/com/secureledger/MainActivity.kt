package com.secureledger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.secureledger.app.SecureLedgerApp
import com.secureledger.app.SecureLedgerTheme

class MainActivity : ComponentActivity() {
    private val sharedImageUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedImageUri.value = sharedImageFrom(intent)
        val application = application as SecureLedgerApplication
        setContent {
            SecureLedgerTheme {
                SecureLedgerApp(application, sharedImageUri.value) { sharedImageUri.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedImageUri.value = sharedImageFrom(intent)
    }

    @Suppress("DEPRECATION")
    private fun sharedImageFrom(intent: Intent?): Uri? =
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } else null
}
