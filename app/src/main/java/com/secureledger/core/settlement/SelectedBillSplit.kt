package com.secureledger.core.settlement

import kotlinx.serialization.Serializable

data class SplitBill(val recordId: String, val payerId: String, val cents: Long)
@Serializable
data class MemberAllocation(val participantId: String, val paidCents: Long, val owedCents: Long)
data class SplitPlan(val recordIds: List<String>, val allocations: List<MemberAllocation>) {
    val totalCents: Long get() = allocations.fold(0L) { sum, it -> Math.addExact(sum, it.owedCents) }
    val balances: List<Balance> get() = allocations.map { Balance(it.participantId, Math.subtractExact(it.paidCents, it.owedCents)) }
}

/** Equal shares use integer cents and assign remainder cents in stable member-ID order. */
object SelectedBillSplit {
    fun calculate(bills: List<SplitBill>, participantIds: List<String>): SplitPlan {
        require(bills.isNotEmpty()) { "请至少选择一笔支出" }
        require(bills.map { it.recordId }.distinct().size == bills.size) { "账单不能重复选择" }
        val ids = participantIds.sorted()
        require(ids.size >= 2 && ids.distinct().size == ids.size && ids.all { it.isNotBlank() }) { "请至少选择两位不同成员" }
        require(bills.all { it.cents >= 0 && it.payerId in ids }) { "所选账单的付款人必须参与结算" }
        val total = bills.fold(0L) { sum, it -> Math.addExact(sum, it.cents) }
        val base = total / ids.size
        val remainder = total % ids.size
        val allocations = ids.mapIndexed { index, id -> MemberAllocation(id,
            bills.filter { it.payerId == id }.fold(0L) { sum, it -> Math.addExact(sum, it.cents) },
            base + if (index < remainder) 1 else 0) }
        return SplitPlan(bills.map { it.recordId }.sorted(), allocations)
    }
}
