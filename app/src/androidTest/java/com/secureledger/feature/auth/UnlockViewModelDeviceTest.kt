package com.secureledger.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secureledger.SecureLedgerApplication
import com.secureledger.core.crypto.KeyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real Keystore-backed unlock state without touching the user's vault. */
@RunWith(AndroidJUnit4::class)
class UnlockViewModelDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun wrongPasswordShowsErrorThenCorrectPasswordOpensHome() = runBlocking {
        val namespace = "unlock_state_test"
        context.getSharedPreferences("secure_vault.$namespace", Context.MODE_PRIVATE).edit().clear().commit()
        val keyManager = KeyManager(context, namespace)
        keyManager.createUser("CorrectPassword123".toCharArray())
        val store = ViewModelStore()
        try {
            val model = model(store, keyManager)
            assertEquals(UnlockUiState.Idle, model.state.value)
            model.submit("WrongPassword123", "", false)
            assertTrue(model.state.value is UnlockUiState.Unlocking)
            val failure = withTimeout(20_000) { model.state.first { it is UnlockUiState.Error } }
            assertTrue((failure as UnlockUiState.Error).message.contains("密码不正确"))
            model.clearError()
            assertEquals(UnlockUiState.Idle, model.state.value)
            model.submit("CorrectPassword123", "", false)
            val success = withTimeout(20_000) { model.state.first { it is UnlockUiState.Success } }
            assertTrue((success as UnlockUiState.Success).unlockPerformance!!.argon2Ms > 0)
        } finally {
            store.clear()
            context.getSharedPreferences("secure_vault.$namespace", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun rotationRetainsUnlockingStateAndRejectsDuplicateSubmit() = runBlocking {
        val namespace = "unlock_rotation_test"
        context.getSharedPreferences("secure_vault.$namespace", Context.MODE_PRIVATE).edit().clear().commit()
        val keyManager = KeyManager(context, namespace)
        val store = ViewModelStore()
        try {
            val firstScreen = model(store, keyManager)
            firstScreen.submit("RotationPassword123", "RotationPassword123", true)
            assertTrue(firstScreen.state.value is UnlockUiState.Unlocking)
            // Recreating a screen keeps the same ViewModelStore, as Activity rotation does.
            val rotatedScreen = model(store, keyManager)
            assertSame(firstScreen, rotatedScreen)
            rotatedScreen.submit("AnotherPassword123", "AnotherPassword123", true)
            val success = withTimeout(20_000) { rotatedScreen.state.first { it is UnlockUiState.Success } }
            assertEquals((success as UnlockUiState.Success).identity.userId, keyManager.unlock("RotationPassword123".toCharArray()).userId)
            assertFalse(runCatching { keyManager.unlock("AnotherPassword123".toCharArray()) }.isSuccess)
        } finally {
            store.clear()
            context.getSharedPreferences("secure_vault.$namespace", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun model(store: ViewModelStore, keyManager: KeyManager): UnlockViewModel =
        ViewModelProvider(store, UnlockViewModel.Factory(keyManager, { error("Unused") }, {
            SecureLedgerApplication.HomeStartupPerformance(0, 0, 0, 0)
        }))[UnlockViewModel::class.java]
}
