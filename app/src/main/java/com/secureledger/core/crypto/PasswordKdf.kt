package com.secureledger.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** Argon2id password KDF. Its parameters are deliberately explicit and versioned. */
object PasswordKdf {
    const val OUTPUT_BYTES = 32
    private const val MEMORY_KIB = 64 * 1024
    private const val ITERATIONS = 3
    private const val PARALLELISM = 1

    fun derive(password: CharArray, salt: ByteArray): ByteArray {
        require(salt.size >= 16) { "Argon2id salt must contain at least 128 bits" }
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(MEMORY_KIB)
                .withIterations(ITERATIONS)
                .withParallelism(PARALLELISM)
                .build()
        )
        return ByteArray(OUTPUT_BYTES).also { output ->
            generator.generateBytes(password, output)
            password.fill('\u0000')
        }
    }
}
