package com.secureledger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.secureledger.app.SecureLedgerApp
import com.secureledger.design.SecureLedgerTheme
import com.secureledger.core.accessibility.TransactionCandidate
import com.secureledger.integration.accessibility.AppVisibility
import com.secureledger.integration.accessibility.CandidateInbox
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    private val sharedImageUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedImageUri.value = sharedImageFrom(intent)
        acceptCandidate(intent)
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
        acceptCandidate(intent)
    }

    override fun onStart() { super.onStart(); AppVisibility.isVisible = true }

    override fun onStop() { AppVisibility.isVisible = false; super.onStop() }

    private fun acceptCandidate(intent: Intent?) {
        val encoded = intent?.getStringExtra(EXTRA_CANDIDATE_JSON) ?: return
        runCatching { Json.decodeFromString<TransactionCandidate>(encoded) }
            .onSuccess(CandidateInbox::offer)
        intent.removeExtra(EXTRA_CANDIDATE_JSON)
    }

    @Suppress("DEPRECATION")
    private fun sharedImageFrom(intent: Intent?): Uri? =
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } else null

    companion object { const val EXTRA_CANDIDATE_JSON = "com.secureledger.CANDIDATE_JSON" }
}
