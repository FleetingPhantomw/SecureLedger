package com.secureledger.core.merkle

import com.secureledger.core.crypto.HashManager
import com.secureledger.core.ledger.SecureRecord
import java.nio.charset.StandardCharsets

/** Deterministic Merkle tree for comparing independently stored ledger replicas. */
class MerkleTree(records: List<SecureRecord>) {
    private val leaves: List<MerkleNode> = records
        .sortedBy { it.recordId }
        .map { record ->
            MerkleNode(
                hash = HashManager.sha256Hex("\u0000${record.recordId}:${record.recordHash}".toByteArray(StandardCharsets.UTF_8)),
                recordId = record.recordId
            )
        }

    private val root: MerkleNode = buildTree(leaves)

    fun calculateRoot(): String = root.hash

    fun compare(other: MerkleTree): Boolean = calculateRoot() == other.calculateRoot()

    fun findDifferences(other: MerkleTree): List<String> {
        val left = leaves.mapNotNull { node -> node.recordId?.let { it to node.hash } }.toMap()
        val right = other.leaves.mapNotNull { node -> node.recordId?.let { it to node.hash } }.toMap()
        return (left.keys + right.keys).filter { id -> left[id] != right[id] }.sorted()
    }

    private fun buildTree(nodes: List<MerkleNode>): MerkleNode {
        if (nodes.isEmpty()) return MerkleNode(HashManager.sha256Hex("EMPTY".toByteArray()))
        if (nodes.size == 1) return nodes.first()
        val parents = nodes.chunked(2).map { pair ->
            val left = pair[0]
            val right = pair.getOrElse(1) { left }
            MerkleNode(HashManager.sha256Hex("\u0001${left.hash}${right.hash}".toByteArray()), left, right)
        }
        return buildTree(parents)
    }
}
