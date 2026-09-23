package com.secureledger.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.secureledger.SecureLedgerApplication
import com.secureledger.core.crypto.KeyManager
import com.secureledger.core.ledger.CorrectionRequest
import com.secureledger.core.ledger.IntegrityReport
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.core.settlement.Balance
import com.secureledger.core.settlement.SettlementProof
import com.secureledger.data.repository.DecryptedRecord
import com.secureledger.data.repository.LedgerRepository
import com.secureledger.data.repository.ProjectRepository
import com.secureledger.data.repository.SettlementRepository
import com.secureledger.data.repository.SyncImportResult
import com.secureledger.data.repository.SyncImportStatus
import com.secureledger.data.repository.SyncRepository
import com.secureledger.data.repository.ImportRepository
import com.secureledger.data.repository.ImportedTransactionView
import com.secureledger.feature.importer.ReconciliationScreen
import com.secureledger.feature.ocr.OcrImportScreen
import com.secureledger.integration.ocr.OcrEngine
import com.secureledger.core.ocr.OrderParser
import com.secureledger.core.ocr.RecognizedOrder
import com.secureledger.core.protocol.QrFrameAssembler
import com.secureledger.core.protocol.QrSyncFrames
import com.secureledger.core.protocol.SecureSyncCodec
import com.secureledger.data.repository.InvitationRepository
import com.secureledger.feature.project.ProjectAccessScreen
import com.secureledger.data.entity.ProjectMemberEntity
import com.secureledger.data.repository.BackupRepository
import com.secureledger.data.repository.BankCardRepository
import com.secureledger.data.entity.BankCardEntity
import com.secureledger.core.ledger.ExpenseCategoryCode
import com.secureledger.core.ledger.PaymentChannelCode
import com.secureledger.feature.settings.SettingsScreen
import com.secureledger.core.accessibility.TransactionCandidate
import com.secureledger.integration.accessibility.CandidateInbox
import com.secureledger.feature.auth.UnlockUiState
import com.secureledger.feature.auth.UnlockViewModel
import com.secureledger.core.backup.PortableBackupCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.secureledger.data.entity.ProjectEntity
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.util.UUID
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private enum class Tab { HOME, LEDGER, PROJECTS, SYNC, SECURITY }

private object LedgerRoute {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ACCESS_JOIN = "access/join"
    const val ACCESS_PROJECT = "access/project/{projectId}"
    const val OCR = "ocr"
    const val RECONCILIATION = "reconciliation"
    const val SETTLEMENT = "settlement/{projectId}"
    const val ADD = "add"
    const val REVIEW = "review"
    const val DETAIL = "detail/{recordId}"
    const val CORRECTION = "correction/{recordId}"
    const val CREATE_PROJECT = "create-project"

    fun accessProject(id: String) = "access/project/${Uri.encode(id)}"
    fun settlement(id: String) = "settlement/${Uri.encode(id)}"
    fun detail(id: String) = "detail/${Uri.encode(id)}"
    fun correction(id: String) = "correction/${Uri.encode(id)}"
}

@Composable
fun SecureLedgerApp(
    application: SecureLedgerApplication,
    sharedImageUri: Uri? = null,
    onSharedImageConsumed: () -> Unit = {}
) {
    var identity by remember { mutableStateOf<KeyManager.UserIdentity?>(null) }
    val unlockViewModel: UnlockViewModel = viewModel(
        factory = remember(application) {
            UnlockViewModel.Factory(
                keyManager = application.keyManager,
                backupRepository = { application.backupRepository },
                prepareHome = application::prepareHome
            )
        }
    )
    val unlockState by unlockViewModel.state.collectAsState()
    LaunchedEffect(unlockState) {
        (unlockState as? UnlockUiState.Success)?.let { identity = it.identity }
    }
    if (identity == null) {
        UnlockScreen(
            firstUse = !application.keyManager.isInitialized(),
            state = unlockState,
            onSubmit = unlockViewModel::submit,
            onRestore = unlockViewModel::restore,
            onInputChanged = unlockViewModel::clearError
        )
    } else {
        LedgerShell(
            application.ledgerRepository,
            application.projectRepository,
            application.settlementRepository,
            application.syncRepository,
            application.importRepository,
            application.ocrEngine,
            application.orderParser,
            application.invitationRepository,
            application.backupRepository,
            application.bankCardRepository,
            sharedImageUri,
            onSharedImageConsumed,
            identity!!,
            onLock = {
                unlockViewModel.reset()
                identity = null
            }
        )
    }
}

@Composable
private fun UnlockScreen(
    firstUse: Boolean,
    state: UnlockUiState,
    onSubmit: (String, String, Boolean) -> Unit,
    onRestore: (suspend () -> ByteArray, String, String, String) -> Unit,
    onInputChanged: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var restoreVisible by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    val isWorking = state is UnlockUiState.Unlocking
    val restorePicker = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            onRestore(
                {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        readBounded(stream, PortableBackupCodec.MAX_BACKUP_BYTES)
                    } ?: error("无法读取备份文件")
                },
                backupPassword,
                password,
                confirmation
            )
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Text("SecureLedger", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("本地优先的可验证安全账本", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))
            Text(if (firstUse) "创建本地账本" else "解锁本地账本", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(password, { password = it; onInputChanged() }, label = { Text("密码（至少 8 位）") },
                enabled = !isWorking, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            if (firstUse) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(confirmation, { confirmation = it; onInputChanged() }, label = { Text("确认密码") },
                    enabled = !isWorking, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
            if (firstUse && restoreVisible) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(backupPassword, { backupPassword = it; onInputChanged() }, label = { Text("备份文件密码") },
                    enabled = !isWorking, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
            (state as? UnlockUiState.Error)?.message?.let {
                Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (isWorking) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(if ((state as UnlockUiState.Unlocking).openingLedger) "正在打开账本…" else "正在解锁…")
                }
            }
            Spacer(Modifier.height(18.dp))
            if (!restoreVisible) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isWorking,
                    onClick = { onSubmit(password, confirmation, firstUse) }
                ) { Text(if (firstUse) "创建并进入" else "解锁") }
            }
            if (firstUse) {
                TextButton(enabled = !isWorking, onClick = {
                    restoreVisible = true
                    if (backupPassword.isNotBlank() && password.length >= 8 && password == confirmation) {
                        restorePicker.launch(arrayOf("application/octet-stream", "text/plain"))
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (restoreVisible) "选择 .sldb 备份并恢复" else "从加密备份恢复")
                }
                if (restoreVisible) {
                    TextButton(enabled = !isWorking, onClick = { restoreVisible = false; backupPassword = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text("取消恢复，创建新账本")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("密码和本机安全模块共同保护你的本地数据。",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerShell(
    repository: LedgerRepository,
    projectRepository: ProjectRepository,
    settlementRepository: SettlementRepository,
    syncRepository: SyncRepository,
    importRepository: ImportRepository,
    ocrEngine: OcrEngine,
    orderParser: OrderParser,
    invitationRepository: InvitationRepository,
    backupRepository: BackupRepository,
    bankCardRepository: BankCardRepository,
    sharedImageUri: Uri?,
    onSharedImageConsumed: () -> Unit,
    identity: KeyManager.UserIdentity,
    onLock: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    var dirtyRoute by remember { mutableStateOf<String?>(null) }
    var discardVisible by remember { mutableStateOf(false) }
    val hasUnsavedChanges = dirtyRoute != null && dirtyRoute == currentEntry?.destination?.route
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var reviewingCandidate by remember { mutableStateOf<TransactionCandidate?>(null) }
    val pendingCandidate by CandidateInbox.pending.collectAsState()
    var latestSettlement by remember { mutableStateOf<SettlementProof?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null) navController.navigate(LedgerRoute.OCR) { launchSingleTop = true }
    }
    val viewModel = remember(identity.userId) {
        LedgerViewModel(
            repository, projectRepository, settlementRepository, syncRepository, importRepository,
            ocrEngine, orderParser, identity, invitationRepository,
            backupRepository, bankCardRepository
        )
    }
    val records by viewModel.records.collectAsStateCompat()
    val projects by viewModel.projects.collectAsStateCompat()
    val report by viewModel.report.collectAsStateCompat()
    val importedTransactions by viewModel.importedTransactions.collectAsStateCompat()
    val projectMembers by viewModel.projectMembers.collectAsStateCompat()
    val bankCards by viewModel.bankCards.collectAsStateCompat()
    val joinRequest = remember(identity.userId) { viewModel.createJoinRequest() }
    NavHost(navController = navController, startDestination = LedgerRoute.MAIN) {
        composable(LedgerRoute.MAIN) {
            LedgerMainTabs(
                tab = tab,
                onTabChange = { tab = it },
                records = records,
                projects = projects,
                importedTransactions = importedTransactions,
                report = report,
                identity = identity,
                onAdd = { navController.navigate(LedgerRoute.ADD) },
                onOpenReconciliation = { navController.navigate(LedgerRoute.RECONCILIATION) },
                onOpenOcr = { navController.navigate(LedgerRoute.OCR) },
                onOpenSettings = { navController.navigate(LedgerRoute.SETTINGS) },
                onOpenRecord = { navController.navigate(LedgerRoute.detail(it.secure.recordId)) },
                onCorrectRecord = { navController.navigate(LedgerRoute.correction(it.secure.recordId)) },
                onCreateProject = { navController.navigate(LedgerRoute.CREATE_PROJECT) },
                onJoinProject = { navController.navigate(LedgerRoute.ACCESS_JOIN) },
                onManageProject = { navController.navigate(LedgerRoute.accessProject(it.projectId)) },
                onSettleProject = { project ->
                    latestSettlement = null
                    viewModel.loadMembers(project.projectId)
                    viewModel.loadLatestSettlement(project.projectId) { latestSettlement = it }
                    navController.navigate(LedgerRoute.settlement(project.projectId))
                },
                onExport = viewModel::exportSyncPackage,
                onImport = viewModel::importSyncPackage,
                onVerify = viewModel::verify
            )
        }
        composable(LedgerRoute.SETTINGS) {
            SettingsScreen(
                identity = identity,
                accessibilityPreferences = (context.applicationContext as SecureLedgerApplication).accessibilityPreferences,
                onBack = navController::popBackStack,
                onLock = { onSharedImageConsumed(); onLock() },
                onCreateBackup = viewModel::createBackup,
                bankCards = bankCards,
                onSaveBankCard = viewModel::saveBankCard,
                onSetBankCardEnabled = viewModel::setBankCardEnabled,
                onDeleteBankCard = viewModel::deleteBankCard
            )
        }
        composable(LedgerRoute.ACCESS_JOIN) {
            ProjectAccessScreen(null, projectMembers, joinRequest, navController::popBackStack,
                viewModel::loadMembers, viewModel::issueInvitation, viewModel::acceptInvitation)
        }
        composable(LedgerRoute.ACCESS_PROJECT) { entry ->
            val project = projects.firstOrNull { it.projectId == entry.arguments?.getString("projectId") }
            if (project != null) {
                ProjectAccessScreen(project, projectMembers, joinRequest, navController::popBackStack,
                    viewModel::loadMembers, viewModel::issueInvitation, viewModel::acceptInvitation)
            }
        }
        composable(LedgerRoute.OCR) {
            OcrImportScreen(
                initialUri = sharedImageUri,
                onInitialUriConsumed = onSharedImageConsumed,
                onBack = navController::popBackStack,
                onRecognize = viewModel::recognizeOrder,
                onParseText = viewModel::parseOrderText,
                onSave = { payload -> viewModel.add(payload) { navController.popBackStack() } }
            )
        }
        composable(LedgerRoute.RECONCILIATION) {
            ReconciliationScreen(importedTransactions, importMessage, navController::popBackStack,
                onSupplement = { id ->
                    viewModel.supplementImport(id) { result ->
                        importMessage = result.fold({ "补记成功" }, { it.message ?: "补记失败" })
                    }
                },
                onIgnore = viewModel::ignoreImport)
        }
        composable(LedgerRoute.SETTLEMENT) { entry ->
            val project = projects.firstOrNull { it.projectId == entry.arguments?.getString("projectId") }
            if (project != null) {
                SettlementScreen(project, identity, projectMembers, latestSettlement,
                    onBack = navController::popBackStack,
                    onCreate = { balances, callback -> viewModel.createSettlement(project.projectId, balances, callback) },
                    onEncode = viewModel::encodeSettlement,
                    onCoSign = viewModel::coSignSettlement,
                    onMerge = viewModel::mergeSettlementSignatures)
            }
        }
        composable(LedgerRoute.ADD) {
            AddRecordScreen(projects, bankCards.filter(BankCardEntity::enabled),
                onBack = { if (hasUnsavedChanges) discardVisible = true else navController.popBackStack() },
                onDirtyChange = { dirtyRoute = if (it) LedgerRoute.ADD else null },
                onSave = { payload, projectId -> viewModel.add(payload, projectId) { navController.popBackStack() } })
        }
        composable(LedgerRoute.REVIEW) {
            LaunchedEffect(reviewingCandidate) {
                if (reviewingCandidate == null) navController.popBackStack()
            }
            reviewingCandidate?.let { candidate ->
                AddRecordScreen(projects, bankCards.filter(BankCardEntity::enabled), candidate,
                    onBack = { if (hasUnsavedChanges) discardVisible = true else navController.popBackStack() },
                    onDirtyChange = { dirtyRoute = if (it) LedgerRoute.REVIEW else null },
                    onSave = { payload, projectId ->
                        viewModel.add(payload, projectId) { navController.popBackStack(); reviewingCandidate = null }
                    })
            }
        }
        composable(LedgerRoute.DETAIL) { entry ->
            val id = entry.arguments?.getString("recordId")
            records.firstOrNull { it.secure.recordId == id }?.let { record ->
                RecordDetailScreen(record, onBack = navController::popBackStack,
                    onCorrect = { navController.navigate(LedgerRoute.correction(record.secure.recordId)) })
            }
        }
        composable(LedgerRoute.CORRECTION) { entry ->
            val id = entry.arguments?.getString("recordId")
            records.firstOrNull { it.secure.recordId == id }?.let { target ->
                CorrectionScreen(target,
                    onBack = { if (hasUnsavedChanges) discardVisible = true else navController.popBackStack() },
                    onDirtyChange = { dirtyRoute = if (it) LedgerRoute.CORRECTION else null },
                    onSave = { request, callback ->
                        viewModel.correct(request) { result ->
                            callback(result)
                            if (result.isSuccess) navController.popBackStack()
                        }
                    })
            }
        }
        composable(LedgerRoute.CREATE_PROJECT) {
            CreateProjectScreen(
                onBack = { if (hasUnsavedChanges) discardVisible = true else navController.popBackStack() },
                onDirtyChange = { dirtyRoute = if (it) LedgerRoute.CREATE_PROJECT else null },
                onSave = { name -> viewModel.createProject(name) { navController.popBackStack() } })
        }
    }
    pendingCandidate?.let { candidate ->
        ModalBottomSheet(onDismissRequest = CandidateInbox::dismiss) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("检测到一笔消费", style = MaterialTheme.typography.titleLarge)
                Text("¥${formatCents(candidate.amountCents)}", style = MaterialTheme.typography.headlineMedium)
                Text(listOf(candidate.merchant, candidate.paymentChannel).filter(String::isNotBlank).joinToString(" · "))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = CandidateInbox::dismiss, modifier = Modifier.weight(1f)) { Text("忽略") }
                    Button(onClick = {
                        reviewingCandidate = candidate
                        CandidateInbox.dismiss()
                        navController.navigate(LedgerRoute.REVIEW)
                    }, modifier = Modifier.weight(1f)) { Text("记一笔") }
                }
            }
        }
    }
    BackHandler(enabled = hasUnsavedChanges && pendingCandidate == null && !discardVisible) {
        discardVisible = true
    }
    if (discardVisible) {
        AlertDialog(
            onDismissRequest = { discardVisible = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("离开后，本页修改不会保留。") },
            confirmButton = { TextButton(onClick = {
                discardVisible = false
                dirtyRoute = null
                navController.popBackStack()
            }) { Text("放弃修改") } },
            dismissButton = { TextButton(onClick = { discardVisible = false }) { Text("继续编辑") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerMainTabs(
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    records: List<DecryptedRecord>,
    projects: List<ProjectEntity>,
    importedTransactions: List<ImportedTransactionView>,
    report: IntegrityReport?,
    identity: KeyManager.UserIdentity,
    onAdd: () -> Unit,
    onOpenReconciliation: () -> Unit,
    onOpenOcr: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecord: (DecryptedRecord) -> Unit,
    onCorrectRecord: (DecryptedRecord) -> Unit,
    onCreateProject: () -> Unit,
    onJoinProject: () -> Unit,
    onManageProject: (ProjectEntity) -> Unit,
    onSettleProject: (ProjectEntity) -> Unit,
    onExport: (String, (Result<String>) -> Unit) -> Unit,
    onImport: (String, (SyncImportResult) -> Unit) -> Unit,
    onVerify: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (tab) { Tab.HOME -> "总览"; Tab.LEDGER -> "个人账本"; Tab.PROJECTS -> "多人项目"; Tab.SYNC -> "同步账本"; Tab.SECURITY -> "安全中心" }) },
                actions = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "新增账目") } }
            )
        },
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.LEDGER) FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, "新增账目")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == Tab.HOME, { onTabChange(Tab.HOME) }, { Icon(Icons.Default.Home, "首页") }, label = { Text("首页") })
                NavigationBarItem(tab == Tab.LEDGER, { onTabChange(Tab.LEDGER) }, { Icon(Icons.AutoMirrored.Filled.MenuBook, "账本") }, label = { Text("账本") })
                NavigationBarItem(tab == Tab.PROJECTS, { onTabChange(Tab.PROJECTS) }, { Icon(Icons.Default.Group, "项目") }, label = { Text("项目") })
                NavigationBarItem(tab == Tab.SYNC, { onTabChange(Tab.SYNC) }, { Icon(Icons.Default.Sync, "同步") }, label = { Text("同步") })
                NavigationBarItem(tab == Tab.SECURITY, { onTabChange(Tab.SECURITY) }, { Icon(Icons.Default.Security, "安全") }, label = { Text("安全") })
            }
        }
    ) { padding ->
        when (tab) {
            Tab.HOME -> HomeScreen(
                padding, records, report,
                hasLegacyImports = importedTransactions.isNotEmpty(),
                onOpenReconciliation = onOpenReconciliation,
                onOpenOcr = onOpenOcr,
                onOpenSettings = onOpenSettings
            )
            Tab.LEDGER -> LedgerScreen(
                padding, records,
                onOpen = onOpenRecord,
                onCorrect = onCorrectRecord
            )
            Tab.PROJECTS -> ProjectScreen(
                padding = padding,
                projects = projects,
                onCreate = onCreateProject,
                onJoin = onJoinProject,
                onManage = onManageProject,
                onSettle = onSettleProject
            )
            Tab.SYNC -> SyncScreen(
                padding = padding,
                projects = projects,
                onExport = onExport,
                onImport = onImport
            )
            Tab.SECURITY -> SecurityScreen(padding, identity, report, onVerify)
        }
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    records: List<DecryptedRecord>,
    report: IntegrityReport?,
    hasLegacyImports: Boolean,
    onOpenReconciliation: () -> Unit,
    onOpenOcr: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("欢迎回来", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("账目", records.size.toString(), Modifier.weight(1f))
            MetricCard("数据状态", if (report?.isValid != false) "正常" else "需检查", Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("本地数据保护", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("账目默认只保存在本机。保存后的原始记录保持不变，如需修改会留下更正记录。")
            }
        }
        if (hasLegacyImports) OutlinedButton(onClick = onOpenReconciliation, modifier = Modifier.fillMaxWidth()) { Text("历史导入记录") }
        Button(onClick = onOpenOcr, modifier = Modifier.fillMaxWidth()) { Text("图片 / 截图识别") }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("设置、备份与恢复") }
        Text("最近账目", style = MaterialTheme.typography.titleMedium)
        records.take(3).forEach { RecordRow(it) }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.bodySmall); Text(value, style = MaterialTheme.typography.headlineSmall) } }
}

@Composable
private fun LedgerScreen(
    padding: PaddingValues,
    records: List<DecryptedRecord>,
    onOpen: (DecryptedRecord) -> Unit,
    onCorrect: (DecryptedRecord) -> Unit
) {
    if (records.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, "空账本", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp)); Text("还没有账目"); Text("点击右上角或右下角 + 开始记录")
        }
    } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(records, key = { it.secure.recordId }) {
            RecordRow(it, onOpen = { onOpen(it) }, onCorrect = { onCorrect(it) })
        }
    }
}

@Composable
private fun ProjectScreen(
    padding: PaddingValues,
    projects: List<ProjectEntity>,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onManage: (ProjectEntity) -> Unit,
    onSettle: (ProjectEntity) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("项目账本", style = MaterialTheme.typography.headlineSmall)
        Text("和成员共同记账、同步记录并完成分摊。", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, "新建项目"); Spacer(Modifier.width(8.dp)); Text("新建多人项目") }
        Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) { Text("加入已有项目") }
        if (projects.isEmpty()) Text("还没有项目", style = MaterialTheme.typography.bodySmall)
        projects.forEach { project ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (project.projectId == ProjectRepository.PERSONAL_PROJECT_ID) "个人账本 · 本机" else "多人项目 · ${project.projectId.takeLast(8)}",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    if (project.projectId != ProjectRepository.PERSONAL_PROJECT_ID) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onManage(project) }, modifier = Modifier.fillMaxWidth()) {
                            Text("成员与邀请")
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { onSettle(project) }, modifier = Modifier.fillMaxWidth()) {
                            Text("打开 AA 结算")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncScreen(
    padding: PaddingValues,
    projects: List<ProjectEntity>,
    onExport: (String, (Result<String>) -> Unit) -> Unit,
    onImport: (String, (SyncImportResult) -> Unit) -> Unit
) {
    var exported by remember { mutableStateOf<String?>(null) }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var frameIndex by remember { mutableStateOf(0) }
    val assembler = remember { QrFrameAssembler() }
    var assemblyProgress by remember { mutableStateOf<String?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var importResult by remember { mutableStateOf<SyncImportResult?>(null) }
    LaunchedEffect(projects) {
        if (projects.none { it.projectId == selectedProjectId }) selectedProjectId = projects.firstOrNull()?.projectId
    }

    fun submitImport(value: String) {
        if (value.isBlank()) {
            operationError = "二维码内容为空"
            return
        }
        operationError = null
        onImport(value) { result -> importResult = result }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { scanResult ->
        scanResult.contents?.let { contents ->
            if (QrSyncFrames.isFrame(contents)) {
                runCatching { assembler.accept(contents) }
                    .onSuccess { progress ->
                        operationError = null
                        assemblyProgress = "已扫描 ${progress.received}/${progress.total} 张"
                        progress.completePackage?.let { complete ->
                            assembler.reset()
                            assemblyProgress = null
                            submitImport(complete)
                        }
                    }.onFailure { operationError = it.message ?: "二维码分片无效" }
            } else if (contents.startsWith(SecureSyncCodec.PREFIX)) {
                assembler.reset()
                assemblyProgress = null
                submitImport(contents)
            } else operationError = "不是 SecureLedger 同步二维码"
        }
    }
    val frameResult = remember(exported) { exported?.let { runCatching { QrSyncFrames.encode(it) } } }
    val frames = frameResult?.getOrNull().orEmpty()
    val qrBitmap = remember(frames, frameIndex) { frames.getOrNull(frameIndex)?.let(::createQrBitmap) }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Sync, "同步账本", tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("同步账本", style = MaterialTheme.typography.headlineSmall)
        }
        Text("通过二维码与其他设备交换账目。收到的数据通过安全验证后才会写入。")
        if (projects.size > 1) {
            Text("选择账本", style = MaterialTheme.typography.labelLarge)
            projects.forEach { project ->
                FilterChip(selectedProjectId == project.projectId, { selectedProjectId = project.projectId; exported = null }, { Text(project.name) })
            }
        }
        Button(onClick = {
            selectedProjectId?.let { projectId ->
                onExport(projectId) { result ->
                    result.onSuccess { exported = it; frameIndex = 0; operationError = null }
                        .onFailure { operationError = it.message ?: "二维码生成失败" }
                }
            }
        }, enabled = selectedProjectId != null, modifier = Modifier.fillMaxWidth()) { Text("生成同步二维码") }
        frameResult?.exceptionOrNull()?.let { Text(it.message ?: "二维码数据过大", color = MaterialTheme.colorScheme.error) }
        if (frames.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(if (frames.size == 1) "同步二维码" else "第 ${frameIndex + 1}/${frames.size} 张", style = MaterialTheme.typography.titleMedium)
                    if (qrBitmap != null) {
                        Image(qrBitmap.asImageBitmap(), "SecureLedger 同步二维码", Modifier.size(260.dp))
                    } else {
                        Text("二维码生成失败，请重新生成同步数据。", color = MaterialTheme.colorScheme.error)
                    }
                    if (frames.size > 1) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedButton(onClick = { frameIndex-- }, enabled = frameIndex > 0) { Text("上一张") }
                            Button(onClick = { frameIndex++ }, enabled = frameIndex < frames.lastIndex) { Text("下一张") }
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                scanner.launch(
                    ScanOptions()
                        .setPrompt("扫描 SecureLedger 同步二维码")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.QrCodeScanner, "扫码导入")
            Spacer(Modifier.width(8.dp))
            Text("扫描二维码")
        }
        assemblyProgress?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        operationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        importResult?.let { result ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (result.accepted) "同步完成" else syncStatusMessage(result.status),
                        color = if (result.accepted) Color(0xFF137333) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("新增 ${result.importedRecords} 条，已存在 ${result.duplicateRecords} 条")
                }
            }
        }
        Text("重复、过期、损坏或来源异常的数据会被拒绝。", style = MaterialTheme.typography.bodySmall)
    }
}

private fun createQrBitmap(value: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 900, 900)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            pixels[y * matrix.width + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
}.getOrNull()

private fun syncStatusMessage(status: SyncImportStatus): String = when (status) {
    SyncImportStatus.ACCEPTED -> "同步完成"
    SyncImportStatus.REPLAY_REJECTED -> "这份数据已经导入过"
    SyncImportStatus.VALIDATION_REJECTED -> "部分记录未通过安全验证"
    SyncImportStatus.PROJECT_UNKNOWN -> "本机尚未加入对应项目"
    SyncImportStatus.KEY_INCOMPATIBLE -> "当前设备无法读取这份数据"
    SyncImportStatus.MALFORMED -> "同步数据格式不正确"
}

private data class ParticipantDraft(val id: String, val paid: String, val owed: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettlementScreen(
    project: ProjectEntity,
    identity: KeyManager.UserIdentity,
    members: List<ProjectMemberEntity>,
    previousProof: SettlementProof?,
    onBack: () -> Unit,
    onCreate: (List<Balance>, (Result<SettlementProof>) -> Unit) -> Unit,
    onEncode: (SettlementProof) -> Result<String>,
    onCoSign: (String, (Result<String>) -> Unit) -> Unit,
    onMerge: (String, (Result<SettlementProof>) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var drafts by remember(project.projectId, members) {
        val ids = members.map(ProjectMemberEntity::userId).distinct()
        mutableStateOf((if (ids.size >= 2) ids else listOf(identity.userId, "")).map { ParticipantDraft(it, "0.00", "0.00") })
    }
    var proof by remember { mutableStateOf(previousProof) }
    var error by remember { mutableStateOf<String?>(null) }
    var approvalInput by remember { mutableStateOf("") }
    var approvalOutput by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(previousProof) { proof = previousProof }
    val approvalQr = remember(approvalOutput) { approvalOutput?.let(::createQrBitmap) }
    val approvalScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { approvalInput = it; error = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AA 结算 · ${project.name}") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("录入每位成员的实际支付与应承担金额，系统会计算净余额并生成最少转账方案。", style = MaterialTheme.typography.bodyMedium)
            drafts.forEachIndexed { index, draft ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("成员 ${index + 1}", style = MaterialTheme.typography.titleSmall)
                            if (drafts.size > 2) {
                                TextButton(onClick = { drafts = drafts.filterIndexed { i, _ -> i != index } }) { Text("移除") }
                            }
                        }
                        OutlinedTextField(
                            value = draft.id,
                            onValueChange = { value -> drafts = drafts.updated(index, draft.copy(id = value)) },
                            label = { Text("成员身份编号") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft.paid,
                                onValueChange = { value -> drafts = drafts.updated(index, draft.copy(paid = value)) },
                                label = { Text("实际支付（元）") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = draft.owed,
                                onValueChange = { value -> drafts = drafts.updated(index, draft.copy(owed = value)) },
                                label = { Text("应承担（元）") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            TextButton(onClick = { drafts = drafts + ParticipantDraft("", "0.00", "0.00") }) {
                Icon(Icons.Default.Add, "添加成员")
                Spacer(Modifier.width(6.dp))
                Text("添加成员")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    runCatching {
                        val ids = drafts.map { it.id.trim() }
                        check(ids.all(String::isNotEmpty)) { "成员 ID 不能为空" }
                        check(ids.distinct().size == ids.size) { "成员 ID 不能重复" }
                        val paid = drafts.map { parseCents(it.paid) }
                        val owed = drafts.map { parseCents(it.owed) }
                        check(paid.sum() == owed.sum()) { "实际支付总额必须等于应承担总额" }
                        drafts.indices.map { i -> Balance(ids[i], paid[i] - owed[i]) }
                    }.onSuccess { balances ->
                        error = null
                        onCreate(balances) { result ->
                            result.onSuccess { proof = it; error = null }
                                .onFailure { error = it.message ?: "结算生成失败" }
                        }
                    }.onFailure { error = it.message ?: "请输入有效金额" }
                }
            ) { Text("计算并保存结算") }
            proof?.let { settlement ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("结算方案已保存", style = MaterialTheme.typography.titleMedium)
                        Text("状态：${if (settlement.verifyHash() && settlement.verifySignatures()) "有效" else "需检查"}")
                        Text("成员确认：${settlement.signatures.map { it.signerId }.distinct().size}/${settlement.participants.distinct().size}")
                        settlement.transfers.forEach { transfer ->
                            Text("${transfer.from} → ${transfer.to}：¥${formatCents(transfer.cents)}")
                        }
                        OutlinedButton(onClick = {
                            onEncode(settlement).onSuccess { approvalOutput = it; error = null }
                                .onFailure { error = it.message ?: "会签包生成失败" }
                        }, modifier = Modifier.fillMaxWidth()) { Text("生成会签二维码") }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("多人确认", style = MaterialTheme.typography.titleMedium)
                    Text("成员扫描待确认内容并确认，再由创建者合并返回的数据。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(approvalInput, { approvalInput = it }, label = { Text("粘贴确认数据") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            approvalScanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("扫描 AA 会签包"))
                        }, modifier = Modifier.weight(1f)) { Text("扫描") }
                        Button(onClick = {
                            onCoSign(approvalInput) { result ->
                                result.onSuccess { approvalOutput = it; error = null }
                                    .onFailure { error = it.message ?: "会签失败" }
                            }
                        }, modifier = Modifier.weight(1f)) { Text("确认") }
                    }
                    Button(onClick = {
                        onMerge(approvalInput) { result ->
                            result.onSuccess { proof = it; approvalOutput = onEncode(it).getOrNull(); error = null }
                                .onFailure { error = it.message ?: "签名合并失败" }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("合并成员确认") }
                    approvalOutput?.let { encoded ->
                        approvalQr?.let { Image(it.asImageBitmap(), "AA 会签二维码", Modifier.size(260.dp).align(Alignment.CenterHorizontally)) }
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SecureLedger AA approval", encoded))
                        }, modifier = Modifier.fillMaxWidth()) { Text("复制确认数据") }
                    }
                }
            }
        }
    }
}

private fun List<ParticipantDraft>.updated(index: Int, value: ParticipantDraft): List<ParticipantDraft> =
    mapIndexed { i, draft -> if (i == index) value else draft }

private fun parseCents(value: String): Long =
    (value.trim().toBigDecimal() * BigDecimal(100)).longValueExact().also { require(it >= 0) { "金额不能为负数" } }

private fun formatCents(cents: Long): String =
    "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateProjectScreen(onBack: () -> Unit, onDirtyChange: (Boolean) -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    SideEffect { onDirtyChange(name.isNotBlank()) }
    Scaffold(topBar = { TopAppBar(title = { Text("新建多人项目") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("创建后，你可以邀请成员共同记账和结算。")
            OutlinedTextField(name, { name = it }, label = { Text("项目名称") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                runCatching { check(name.trim().isNotEmpty()) { "项目名称不能为空" }; onSave(name) }
                    .onFailure { error = it.message ?: "创建失败" }
            }, modifier = Modifier.fillMaxWidth()) { Text("创建项目") }
        }
    }
}

@Composable
private fun RecordRow(record: DecryptedRecord, onOpen: (() -> Unit)? = null, onCorrect: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(record.payload.merchant.ifBlank { "未命名商户" }, style = MaterialTheme.typography.titleMedium)
                Text("${record.payload.category} · ${record.payload.paymentChannel}", style = MaterialTheme.typography.bodySmall)
                Text(formatTime(record.payload.transactionTime), style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("¥${record.payload.amountCents / 100}.${(record.payload.amountCents % 100).toString().padStart(2, '0')}", style = MaterialTheme.typography.titleMedium)
                if (onCorrect != null && record.payload.recordType != "CORRECTION") {
                    TextButton(onClick = onCorrect) { Text("更正") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecordScreen(
    projects: List<ProjectEntity>,
    bankCards: List<BankCardEntity>,
    candidate: TransactionCandidate? = null,
    onBack: () -> Unit,
    onDirtyChange: (Boolean) -> Unit,
    onSave: (PlainRecordPayload, String) -> Unit
) {
    val context = LocalContext.current
    var recordType by remember { mutableStateOf("EXPENSE") }
    var projectId by remember { mutableStateOf(ProjectRepository.PERSONAL_PROJECT_ID) }
    var amount by remember(candidate) { mutableStateOf(candidate?.amountCents?.let(::formatCents).orEmpty()) }
    var merchant by remember(candidate) { mutableStateOf(candidate?.merchant.orEmpty()) }
    var category by remember(candidate) { mutableStateOf(if (candidate == null) ExpenseCategoryCode.FOOD_TOBACCO_ALCOHOL else ExpenseCategoryCode.fromLegacy(candidate.merchant)) }
    var note by remember { mutableStateOf("") }
    var paymentChannel by remember(candidate) { mutableStateOf(candidate?.paymentChannel?.let(PaymentChannelCode::fromLegacy)?.takeIf { it != PaymentChannelCode.LEGACY_OTHER } ?: PaymentChannelCode.ALIPAY) }
    var selectedBankCardId by remember { mutableStateOf<String?>(null) }
    var tags by remember(candidate) { mutableStateOf(if (candidate == null) "" else "auto-detected,user-confirmed") }
    val initialTime = remember(candidate) { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(candidate?.transactionTime ?: System.currentTimeMillis())) }
    var transactionTime by remember(candidate) { mutableStateOf(initialTime) }
    var vouchers by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val changed = recordType != "EXPENSE" || projectId != ProjectRepository.PERSONAL_PROJECT_ID ||
        amount != candidate?.amountCents?.let(::formatCents).orEmpty() || merchant != candidate?.merchant.orEmpty() ||
        category != (if (candidate == null) ExpenseCategoryCode.FOOD_TOBACCO_ALCOHOL else ExpenseCategoryCode.fromLegacy(candidate.merchant)) ||
        note.isNotBlank() || paymentChannel != (candidate?.paymentChannel?.let(PaymentChannelCode::fromLegacy)?.takeIf { it != PaymentChannelCode.LEGACY_OTHER } ?: PaymentChannelCode.ALIPAY) ||
        selectedBankCardId != null || tags != (if (candidate == null) "" else "auto-detected,user-confirmed") ||
        transactionTime != initialTime || vouchers.isNotEmpty()
    SideEffect { onDirtyChange(changed) }
    val voucherPicker = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.take(5).forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        vouchers = uris.take(5).map(Uri::toString)
    }
    Scaffold(topBar = { TopAppBar(title = { Text(if (candidate == null) "新增账目" else "确认消费") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (candidate == null) "记录真实交易金额与时间；保存后原始记录不可直接修改。" else "请核对金额、商户和支付方式后保存。", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(recordType == "EXPENSE", { recordType = "EXPENSE" }, { Text("支出") })
                FilterChip(recordType == "INCOME", { recordType = "INCOME" }, { Text("收入") })
                FilterChip(recordType == "TRANSFER", { recordType = "TRANSFER" }, { Text("转账") })
            }
            Text("账本", style = MaterialTheme.typography.labelLarge)
            projects.forEach { project ->
                FilterChip(projectId == project.projectId, { projectId = project.projectId }, { Text(project.name) })
            }
            OutlinedTextField(amount, { amount = it }, label = { Text("金额（元）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(merchant, { merchant = it }, label = { Text("商户") }, modifier = Modifier.fillMaxWidth())
            Text("分类", style = MaterialTheme.typography.labelLarge)
            ExpenseCategoryCode.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option.displayName) }
                        )
                    }
                }
            }
            OutlinedTextField(transactionTime, { transactionTime = it }, label = { Text("实际交易时间（yyyy-MM-dd HH:mm）") }, modifier = Modifier.fillMaxWidth())
            Text("支付方式", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentChannelCode.entries.filter { it != PaymentChannelCode.LEGACY_OTHER }.forEach { option ->
                    FilterChip(
                        selected = paymentChannel == option,
                        onClick = { paymentChannel = option },
                        label = { Text(option.displayName) }
                    )
                }
            }
            if (paymentChannel == PaymentChannelCode.BANK_CARD) {
                if (bankCards.isEmpty()) {
                    Text("请先在设置中添加并启用银行卡（仅保存银行、别名和后四位）。", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("选择银行卡", style = MaterialTheme.typography.labelLarge)
                    bankCards.forEach { card ->
                        FilterChip(
                            selected = selectedBankCardId == card.id,
                            onClick = { selectedBankCardId = card.id },
                            label = { Text(card.displaySnapshot) }
                        )
                    }
                }
            }
            OutlinedTextField(tags, { tags = it }, label = { Text("标签（逗号分隔）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedButton(onClick = { voucherPicker.launch(arrayOf("image/*", "application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (vouchers.isEmpty()) "添加凭证（最多 5 个）" else "已选择 ${vouchers.size} 个凭证")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                runCatching {
                    val cents = (amount.toBigDecimal() * java.math.BigDecimal(100)).longValueExact()
                    check(cents >= 0) { "金额不能为负数" }
                    val time = java.time.LocalDateTime.parse(transactionTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val bankCard = if (paymentChannel == PaymentChannelCode.BANK_CARD) {
                        bankCards.firstOrNull { it.id == selectedBankCardId }
                            ?: error("请选择已启用的银行卡")
                    } else null
                    onSave(PlainRecordPayload(recordType = recordType, amountCents = cents, category = category.displayName,
                        merchant = merchant, note = note, account = "default",
                        paymentChannel = paymentChannel.displayName,
                        paymentChannelCode = paymentChannel.name,
                        bankCardId = bankCard?.id,
                        bankCardDisplaySnapshot = bankCard?.displaySnapshot,
                        primaryCategoryCode = category.name,
                        voucherRefs = vouchers, tags = tags.split(',', '，').map(String::trim).filter(String::isNotBlank),
                        transactionTime = time), projectId)
                }.onFailure { error = it.message ?: "金额格式错误" }
            }) { Text("保存") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailScreen(record: DecryptedRecord, onBack: () -> Unit, onCorrect: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("账目详情") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(record.payload.merchant.ifBlank { "未命名商户" }, style = MaterialTheme.typography.headlineSmall)
            Text("${recordTypeLabel(record.payload.recordType)} · ¥${formatCents(record.payload.amountCents)}", style = MaterialTheme.typography.titleLarge)
            Text("分类：${record.payload.category}")
            Text("交易时间：${DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(record.payload.transactionTime))}")
            Text("支付方式：${record.payload.paymentChannel}")
            record.payload.bankCardDisplaySnapshot?.let { Text("银行卡：$it") }
            if (record.payload.tags.isNotEmpty()) Text("标签：${record.payload.tags.joinToString("、")}")
            if (record.payload.note.isNotBlank()) Text("备注：${record.payload.note}")
            if (record.payload.payments.isNotEmpty()) {
                Text("支付明细", style = MaterialTheme.typography.titleMedium)
                record.payload.payments.forEach { Text("${it.channel}：¥${formatCents(it.amountCents)}${if (it.isBankDirect) "（银行直付）" else ""}") }
            }
            if (record.payload.voucherRefs.isNotEmpty()) Text("凭证：${record.payload.voucherRefs.size} 个（仅保存本机授权引用）")
            if (record.correctionHistory.isNotEmpty()) {
                Text("更正审计历史", style = MaterialTheme.typography.titleMedium)
                Text("原始金额：¥${record.correctionHistory.first().oldValue}")
                record.correctionHistory.forEach { correction ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${correction.oldValue} → ${correction.newValue} · ${formatTime(correction.createdAt)}")
                            Text("原因：${correction.reason.ifBlank { "未说明" }}", style = MaterialTheme.typography.bodySmall)
                            Text("更正记录：${correction.recordId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(onClick = onCorrect, modifier = Modifier.fillMaxWidth()) { Text("更正此记录") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorrectionScreen(
    target: DecryptedRecord,
    onBack: () -> Unit,
    onDirtyChange: (Boolean) -> Unit,
    onSave: (CorrectionRequest, (Result<Unit>) -> Unit) -> Unit
) {
    val currentAmount = "${target.payload.amountCents / 100}.${(target.payload.amountCents % 100).toString().padStart(2, '0')}"
    var newAmount by remember { mutableStateOf(currentAmount) }
    var reason by remember { mutableStateOf("金额录入错误") }
    var error by remember { mutableStateOf<String?>(null) }
    SideEffect { onDirtyChange(newAmount != currentAmount || reason != "金额录入错误") }
    Scaffold(topBar = { TopAppBar(title = { Text("追加更正记录") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("原始记录保持不变，本次修改会作为新的更正记录保存。")
            Text("当前有效金额：¥$currentAmount", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(newAmount, { newAmount = it }, label = { Text("更正后金额（元）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(reason, { reason = it }, label = { Text("更正原因") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                runCatching {
                    val newCents = (newAmount.toBigDecimal() * java.math.BigDecimal(100)).longValueExact()
                    check(newCents >= 0) { "金额不能为负数" }
                    check(newCents != target.payload.amountCents) { "更正后金额与原金额相同" }
                    onSave(CorrectionRequest("c_${UUID.randomUUID()}", target.secure.recordId, currentAmount, newAmount, reason, System.currentTimeMillis())) { result ->
                        result.onFailure { error = it.message ?: "更正追加失败" }
                    }
                }.onFailure { error = it.message ?: "更正失败" }
            }) { Text("保存更正") }
        }
    }
}

@Composable
private fun SecurityScreen(padding: PaddingValues, identity: KeyManager.UserIdentity, report: IntegrityReport?, verify: () -> Unit) {
    var showTechnicalDetails by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Security, "安全中心", tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("安全中心", style = MaterialTheme.typography.headlineSmall) }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("本机身份", style = MaterialTheme.typography.labelLarge)
            Text(identity.userId)
        } }
        Button(onClick = verify, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock, "验证"); Spacer(Modifier.width(8.dp)); Text("验证账本完整性") }
        report?.let { current ->
            Text(if (current.isValid) "验证通过：${current.checkedRecords} 条记录" else "发现 ${current.issues.size} 个问题",
                color = if (current.isValid) Color(0xFF137333) else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium)
            Text("上次检查：${DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(current.checkedAt))}", style = MaterialTheme.typography.bodySmall)
            if (!current.isValid) Text("部分记录未通过安全验证。", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) {
                Text(if (showTechnicalDetails) "收起技术详情" else "查看技术详情")
            }
            if (showTechnicalDetails) {
                Text("Merkle Root：${current.merkleRoot}", style = MaterialTheme.typography.bodySmall)
                Text("签名：${if (current.issues.none { it.failure.name == "SIGNATURE_INVALID" }) "通过" else "异常"} · 哈希链：${if (current.issues.none { it.failure.name == "PREVIOUS_HASH_INVALID" || it.failure.name == "SEQUENCE_GAP" }) "通过" else "异常"}")
                current.issues.forEach { issue -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("记录 ${issue.recordId}", style = MaterialTheme.typography.titleSmall)
                    Text(issue.failure.name); Text(issue.detail, style = MaterialTheme.typography.bodySmall)
                } } }
                Text("检查项：recordHash、Ed25519、previousHash、序列连续性和 AES-GCM 认证标签。", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("安全验证会检查记录是否完整、来源是否可信，以及历史是否连续。", style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatTime(value: Long): String = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))

private fun recordTypeLabel(value: String): String = when (value) {
    "EXPENSE" -> "支出"
    "INCOME" -> "收入"
    "TRANSFER" -> "转账"
    "CORRECTION" -> "更正"
    else -> value
}

private fun readBounded(stream: java.io.InputStream, maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        require(output.size() <= maxBytes) { "文件超过大小限制" }
    }
    return output.toByteArray()
}

@Composable
private fun <T> StateFlow<T>.collectAsStateCompat(): androidx.compose.runtime.State<T> =
    this.collectAsState()

@Composable
fun SecureLedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
