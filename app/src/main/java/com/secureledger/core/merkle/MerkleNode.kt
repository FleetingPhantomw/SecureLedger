package com.secureledger.core.merkle

/** A Merkle node with optional source record id for difference explanations. */
data class MerkleNode(
    val hash: String,
    val left: MerkleNode? = null,
    val right: MerkleNode? = null,
    val recordId: String? = null
)
