package com.secureledger

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secureledger.core.ledger.PlainRecordPayload
import com.secureledger.core.settlement.*
import com.secureledger.core.protocol.SettlementExchangeCodec
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in two-device scenario. Uses only a labeled test project and never clears a user's vault. */
@RunWith(AndroidJUnit4::class)
class TwoDeviceAaScenarioTest {
    @Test fun exchangeSelectedBillsAndIndependentApprovals() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit device scenario not requested", args.getString("aaScenario") == "true")
        val app = ApplicationProvider.getApplicationContext<SecureLedgerApplication>()
        val password = requireNotNull(args.getString("vaultPassword")).toCharArray()
        val identity = try { app.keyManager.unlock(password) } finally { password.fill('\u0000') }
        val directory = File(app.filesDir, "aa-device-test").apply { mkdirs() }
        fun input(name: String) = File(directory, name).readText()
        fun output(name: String, value: String) = File(directory, name).writeText(value)
        val projectId = requireNotNull(args.getString("testProjectId"))
        fun bill(amount: Long, merchant: String) = PlainRecordPayload(recordType = "EXPENSE", amountCents = amount, category = "其他", merchant = merchant,
            note = "双设备 AA 功能测试", account = "default", paymentChannel = "其他", tags = listOf("device-test"), transactionTime = System.currentTimeMillis())
        when (args.getString("stage")) {
            "request" -> output("request.txt", app.invitationRepository.createJoinRequest(identity, "手机成员"))
            "invite" -> {
                val request = input("request.txt")
                app.projectRepository.createProject(identity, projectId, "联机测试 2026-10-07")
                output("invitation.txt", app.invitationRepository.issueInvitation(identity, projectId, request))
                app.ledgerRepository.addRecord(identity, bill(12000, "测试餐费"), projectId)
                app.ledgerRepository.addRecord(identity, bill(4501, "测试门票"), projectId)
                output("sync.txt", app.syncRepository.export(identity, projectId))
            }
            "join" -> {
                app.invitationRepository.acceptInvitation(identity, input("invitation.txt"))
                val result = app.syncRepository.import(identity, input("sync.txt"))
                assertTrue(result.details.toString(), result.accepted)
                assertEquals(2, result.importedRecords)
                app.ledgerRepository.addRecord(identity, bill(7500, "测试住宿"), projectId)
                output("sync.txt", app.syncRepository.export(identity, projectId))
                assertEquals(2, app.projectRepository.members(projectId).size)
            }
            "settle" -> {
                assertTrue(app.syncRepository.import(identity, input("sync.txt")).accepted)
                val records = app.ledgerRepository.observeRecords(identity).first().filter { it.secure.projectId == projectId }
                assertEquals(3, records.size)
                val ids = app.projectRepository.members(projectId).map { it.userId }
                val plan = SelectedBillSplit.calculate(records.map { SplitBill(it.secure.recordId, it.secure.authorId, it.payload.amountCents) }, ids)
                assertEquals(24001L, plan.totalCents)
                assertEquals(listOf(12000L, 12001L), plan.allocations.map { it.owedCents }.sorted())
                val proof = app.settlementRepository.createAndSave(identity, projectId, app.ledgerRepository.projectMerkleRoot(projectId), plan.balances, plan)
                assertEquals(3, proof.selectedRecordIds.size)
                output("approval.txt", app.settlementRepository.encodeForApproval(proof))
                output("sync.txt", app.syncRepository.export(identity, projectId))
            }
            "approve" -> {
                assertTrue(app.syncRepository.import(identity, input("sync.txt")).accepted)
                val encoded = app.settlementRepository.coSign(identity, input("approval.txt"))
                val proof = SettlementExchangeCodec.decode(encoded)
                assertEquals(2, proof.signatures.size)
                assertNotNull(app.settlementRepository.latest(projectId, identity))
                output("approval.txt", encoded)
            }
            "merge" -> {
                val proof = app.settlementRepository.mergeSignatures(identity, input("approval.txt"))
                assertTrue(proof.verifyHash() && proof.verifySignatures())
                assertEquals(2, proof.signatures.size)
                assertEquals(24001L, proof.allocations.sumOf { it.owedCents })
                assertTrue(app.ledgerRepository.verify(identity).isValid)
            }
            else -> error("Unknown scenario stage")
        }
    }
}
