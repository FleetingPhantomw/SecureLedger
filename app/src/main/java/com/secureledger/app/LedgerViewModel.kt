package com.secureledger.app

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.importer.DataSourceType
import com.secureledger.core.ledger.CorrectionRequest
import com.secureledger.core.ledger.IntegrityReport
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.core.ocr.OrderParser
import com.secureledger.core.ocr.RecognizedOrder
import com.secureledger.core.settlement.Balance
import com.secureledger.core.settlement.SettlementProof
import com.secureledger.data.entity.ProjectEntity
import com.secureledger.data.entity.ProjectMemberEntity
import com.secureledger.data.repository.BackupRepository
import com.secureledger.data.repository.BankCardRepository
import com.secureledger.data.repository.DecryptedRecord
import com.secureledger.data.repository.ImportRepository
import com.secureledger.data.repository.ImportedTransactionView
import com.secureledger.data.repository.InvitationRepository
import com.secureledger.data.repository.LedgerRepository
import com.secureledger.data.repository.ProjectRepository
import com.secureledger.data.repository.SettlementRepository
import com.secureledger.data.repository.SyncImportResult
import com.secureledger.data.repository.SyncRepository
import com.secureledger.data.entity.BankCardEntity
import com.secureledger.integration.network.LanShareSession
import com.secureledger.integration.network.LanSyncController
import com.secureledger.integration.network.RelaySyncClient
import com.secureledger.integration.ocr.OcrEngine
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Presentation state coordinator; repositories retain all persistence and security rules. */
internal class LedgerViewModel(
    private val repository: LedgerRepository,
    private val projectRepository: ProjectRepository,
    private val settlementRepository: SettlementRepository,
    private val syncRepository: SyncRepository,
    private val importRepository: ImportRepository,
    private val ocrEngine: OcrEngine,
    private val orderParser: OrderParser,
    private val identity: KeyManager.UserIdentity,
    private val invitationRepository: InvitationRepository,
    private val lanSyncController: LanSyncController,
    private val relaySyncClient: RelaySyncClient,
    private val backupRepository: BackupRepository,
    private val bankCardRepository: BankCardRepository
) : ViewModel() {
    private val _records = MutableStateFlow<List<DecryptedRecord>>(emptyList())
    val records: StateFlow<List<DecryptedRecord>> = _records.asStateFlow()
    private val _projects = MutableStateFlow<List<ProjectEntity>>(emptyList())
    val projects: StateFlow<List<ProjectEntity>> = _projects.asStateFlow()
    private val _report = MutableStateFlow<IntegrityReport?>(null)
    val report: StateFlow<IntegrityReport?> = _report.asStateFlow()
    private val _importedTransactions = MutableStateFlow<List<ImportedTransactionView>>(emptyList())
    val importedTransactions: StateFlow<List<ImportedTransactionView>> = _importedTransactions.asStateFlow()
    private val _projectMembers = MutableStateFlow<List<ProjectMemberEntity>>(emptyList())
    val projectMembers: StateFlow<List<ProjectMemberEntity>> = _projectMembers.asStateFlow()
    private val _bankCards = MutableStateFlow<List<BankCardEntity>>(emptyList())
    val bankCards: StateFlow<List<BankCardEntity>> = _bankCards.asStateFlow()

    init {
        viewModelScope.launch {
            projectRepository.ensurePersonalProject(identity)
            projectRepository.observeProjects().collect { _projects.value = it }
        }
        viewModelScope.launch {
            repository.observeRecords(identity).flowOn(Dispatchers.Default).collect { _records.value = it }
        }
        viewModelScope.launch {
            importRepository.observeTransactions(identity).flowOn(Dispatchers.Default)
                .collect { _importedTransactions.value = it }
        }
        viewModelScope.launch { bankCardRepository.observeAll().collect { _bankCards.value = it } }
        // Full chain/signature/Merkle validation is intentionally deferred until after home is visible.
        viewModelScope.launch { refreshIntegrity() }
    }

    fun add(payload: PlainRecordPayload, projectId: String = ProjectRepository.PERSONAL_PROJECT_ID, onDone: () -> Unit) = viewModelScope.launch {
        repository.addRecord(identity, payload, projectId)
        refreshIntegrity()
        onDone()
    }

    fun correct(request: CorrectionRequest, callback: (Result<Unit>) -> Unit) = viewModelScope.launch {
        callback(runCatching {
            repository.addCorrection(identity, request)
            refreshIntegrity()
        })
    }

    fun createProject(name: String, onDone: () -> Unit) = viewModelScope.launch {
        projectRepository.createProject(identity, name = name)
        onDone()
    }

    fun createJoinRequest(): String = invitationRepository.createJoinRequest(identity, "我")

    fun loadMembers(projectId: String) = viewModelScope.launch {
        _projectMembers.value = projectRepository.members(projectId)
    }

    fun issueInvitation(projectId: String, request: String, callback: (Result<String>) -> Unit) = viewModelScope.launch {
        callback(runCatching { invitationRepository.issueInvitation(identity, projectId, request) })
    }

    fun acceptInvitation(invitation: String, callback: (Result<ProjectEntity>) -> Unit) = viewModelScope.launch {
        callback(runCatching { invitationRepository.acceptInvitation(identity, invitation) })
    }

    fun loadLatestSettlement(projectId: String, onLoaded: (SettlementProof?) -> Unit) = viewModelScope.launch {
        onLoaded(settlementRepository.latest(projectId, identity))
    }

    fun createSettlement(projectId: String, balances: List<Balance>, callback: (Result<SettlementProof>) -> Unit) =
        viewModelScope.launch {
            callback(runCatching {
                val report = withContext(Dispatchers.Default) { repository.verify(identity) }
                check(report.isValid) { "账本完整性验证失败，不能生成结算凭证" }
                settlementRepository.createAndSave(identity, projectId, repository.projectMerkleRoot(projectId), balances)
            })
        }

    fun encodeSettlement(proof: SettlementProof): Result<String> = runCatching { settlementRepository.encodeForApproval(proof) }

    fun coSignSettlement(encoded: String, callback: (Result<String>) -> Unit) = viewModelScope.launch {
        callback(runCatching { settlementRepository.coSign(identity, encoded.trim()) })
    }

    fun mergeSettlementSignatures(encoded: String, callback: (Result<SettlementProof>) -> Unit) = viewModelScope.launch {
        callback(runCatching { settlementRepository.mergeSignatures(identity, encoded.trim()) })
    }

    fun exportSyncPackage(projectId: String, callback: (Result<String>) -> Unit) = viewModelScope.launch {
        callback(runCatching { syncRepository.export(identity, projectId) })
    }

    fun importSyncPackage(encoded: String, callback: (SyncImportResult) -> Unit) = viewModelScope.launch {
        val result = syncRepository.import(identity, encoded)
        if (result.accepted) refreshIntegrity()
        callback(result)
    }

    fun startLan(projectId: String, encoded: String): Result<LanShareSession> = runCatching { lanSyncController.start(projectId, encoded) }
    fun stopLan() = lanSyncController.stop()

    fun receiveLan(url: String, token: String, callback: (Result<SyncImportResult>) -> Unit) = viewModelScope.launch {
        callback(runCatching { syncRepository.import(identity, lanSyncController.fetch(url, token)) })
    }

    fun relayUpload(endpoint: String, token: String, encoded: String, callback: (Result<String>) -> Unit) = viewModelScope.launch {
        callback(runCatching { relaySyncClient.upload(endpoint, token, encoded) })
    }

    fun relayDownload(endpoint: String, token: String, callback: (Result<SyncImportResult>) -> Unit) = viewModelScope.launch {
        callback(runCatching { syncRepository.import(identity, relaySyncClient.download(endpoint, token)) })
    }

    fun createBackup(password: CharArray, callback: (Result<ByteArray>) -> Unit) = viewModelScope.launch {
        try { callback(runCatching { backupRepository.export(identity, password) }) }
        finally { password.fill('\u0000') }
    }

    fun importCsv(context: Context, sourceType: DataSourceType, uri: Uri, displayName: String,
                  callback: (Result<com.secureledger.data.repository.ImportSummary>) -> Unit) = viewModelScope.launch {
        callback(runCatching {
            val bytes = withContext(Dispatchers.IO) {
                context.applicationContext.contentResolver.openInputStream(uri)?.use(::readBounded)
                    ?: error("无法读取所选文件")
            }
            importRepository.importCsv(identity, sourceType, displayName, bytes)
        })
    }

    fun supplementImport(importedId: String, callback: (Result<String>) -> Unit) = viewModelScope.launch {
        callback(runCatching { importRepository.supplement(identity, importedId) })
        refreshIntegrity()
    }

    fun ignoreImport(importedId: String) = viewModelScope.launch { importRepository.ignore(importedId) }
    fun recognizeOrder(uri: Uri, callback: (Result<RecognizedOrder>) -> Unit) = viewModelScope.launch {
        callback(runCatching { orderParser.parse(ocrEngine.recognize(uri).text) })
    }
    fun parseOrderText(text: String): Result<RecognizedOrder> = runCatching { orderParser.parse(text) }
    fun verify() = viewModelScope.launch { refreshIntegrity() }

    fun saveBankCard(
        id: String?,
        bankName: String,
        alias: String,
        lastFour: String,
        callback: (Result<Unit>) -> Unit
    ) = viewModelScope.launch {
        callback(runCatching { bankCardRepository.save(id, bankName, alias, lastFour) })
    }

    fun setBankCardEnabled(card: BankCardEntity, enabled: Boolean) = viewModelScope.launch {
        bankCardRepository.setEnabled(card, enabled)
    }

    fun deleteBankCard(card: BankCardEntity) = viewModelScope.launch {
        bankCardRepository.delete(card)
    }

    private suspend fun refreshIntegrity() {
        val started = SystemClock.elapsedRealtime()
        _report.value = withContext(Dispatchers.Default) { repository.verify(identity) }
        Log.d("SecureLedgerPerf", "integrityValidation=${SystemClock.elapsedRealtime() - started}ms records=${_report.value?.checkedRecords ?: 0}")
    }

    override fun onCleared() {
        lanSyncController.stop()
        super.onCleared()
    }

    private fun readBounded(stream: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            require(total <= ImportRepository.MAX_FILE_BYTES) { "文件超过 5 MiB 限制" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
