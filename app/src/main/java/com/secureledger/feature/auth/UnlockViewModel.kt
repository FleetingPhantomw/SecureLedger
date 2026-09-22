package com.secureledger.feature.auth

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.secureledger.SecureLedgerApplication
import com.secureledger.core.crypto.KeyManager
import com.secureledger.data.repository.BackupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UnlockUiState {
    data object Idle : UnlockUiState
    data class Unlocking(val openingLedger: Boolean = false) : UnlockUiState
    data class Success(
        val identity: KeyManager.UserIdentity,
        val unlockPerformance: KeyManager.UnlockPerformance?,
        val homePerformance: SecureLedgerApplication.HomeStartupPerformance,
        val totalMs: Long
    ) : UnlockUiState
    data class Error(val message: String) : UnlockUiState
}

/** Keeps expensive vault work and its rotation-safe UI state outside Compose. */
internal class UnlockViewModel(
    private val keyManager: KeyManager,
    private val backupRepository: () -> BackupRepository,
    private val prepareHome: suspend (KeyManager.UserIdentity) -> SecureLedgerApplication.HomeStartupPerformance,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _state = MutableStateFlow<UnlockUiState>(UnlockUiState.Idle)
    val state: StateFlow<UnlockUiState> = _state.asStateFlow()

    fun submit(passwordText: String, confirmationText: String, firstUse: Boolean) {
        if (_state.value is UnlockUiState.Unlocking) return
        val password = passwordText.toCharArray()
        start {
            try {
                require(!firstUse || passwordText == confirmationText) { "两次输入的密码不一致" }
                if (firstUse) {
                    withContext(cpuDispatcher) { KeyManager.ProfiledUnlock(keyManager.createUser(password), zeroPerformance()) }
                } else {
                    withContext(cpuDispatcher) { keyManager.unlockProfiled(password) }
                }
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun restore(
        loadBytes: suspend () -> ByteArray,
        backupPasswordText: String,
        newPasswordText: String,
        confirmationText: String
    ) {
        if (_state.value is UnlockUiState.Unlocking) return
        val backupPassword = backupPasswordText.toCharArray()
        val newPassword = newPasswordText.toCharArray()
        start {
            try {
                require(newPasswordText == confirmationText) { "两次新保险库密码不一致" }
                val identity = withContext(ioDispatcher) {
                    backupRepository().restore(loadBytes(), backupPassword, newPassword)
                }
                KeyManager.ProfiledUnlock(identity, zeroPerformance())
            } finally {
                backupPassword.fill('\u0000')
                newPassword.fill('\u0000')
            }
        }
    }

    fun clearError() {
        if (_state.value is UnlockUiState.Error) _state.value = UnlockUiState.Idle
    }

    fun reset() {
        _state.value = UnlockUiState.Idle
    }

    private fun start(identityBlock: suspend () -> KeyManager.ProfiledUnlock) {
        _state.value = UnlockUiState.Unlocking()
        viewModelScope.launch {
            val totalStarted = SystemClock.elapsedRealtime()
            runCatching {
                val profiled = identityBlock()
                _state.value = UnlockUiState.Unlocking(openingLedger = true)
                val home = prepareHome(profiled.identity)
                UnlockUiState.Success(
                    identity = profiled.identity,
                    unlockPerformance = profiled.performance.takeUnless { it == zeroPerformance() },
                    homePerformance = home,
                    totalMs = SystemClock.elapsedRealtime() - totalStarted
                )
            }.onSuccess { success ->
                _state.value = success
                Log.d("SecureLedgerPerf", "unlockFlow total=${success.totalMs}ms argon2=${success.unlockPerformance?.argon2Ms ?: -1}ms")
            }.onFailure { failure ->
                _state.value = UnlockUiState.Error(userFacingMessage(failure))
            }
        }
    }

    private fun zeroPerformance() = KeyManager.UnlockPerformance(0, 0, 0, 0, 0, 0)

    private fun userFacingMessage(failure: Throwable): String {
        val message = failure.message.orEmpty()
        return when {
            message.contains("两次") || message.contains("至少") || message.contains("备份") -> message
            message.contains("tag", ignoreCase = true) || message.contains("mac", ignoreCase = true) ->
                "密码不正确，或本地数据已损坏"
            else -> "无法打开本地账本，请重试"
        }
    }

    class Factory(
        private val keyManager: KeyManager,
        private val backupRepository: () -> BackupRepository,
        private val prepareHome: suspend (KeyManager.UserIdentity) -> SecureLedgerApplication.HomeStartupPerformance
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UnlockViewModel(keyManager, backupRepository, prepareHome) as T
    }
}
