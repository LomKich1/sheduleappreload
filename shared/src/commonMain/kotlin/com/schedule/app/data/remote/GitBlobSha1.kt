package com.schedule.app.data.remote

/** Portable SHA-1 for verifying Git blob content on every supported platform. */
internal fun gitBlobSha1(bytes: ByteArray): String {
    val header = "blob ${bytes.size}\u0000".encodeToByteArray()
    return Sha1.digest(header + bytes).joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

private object Sha1 {
    fun digest(input: ByteArray): ByteArray {
        val bitLength = input.size.toLong() * 8L
        val zeroPadding = (56 - (input.size + 1) % 64 + 64) % 64
        val padded = ByteArray(input.size + 1 + zeroPadding + 8)
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        repeat(8) { index ->
            padded[padded.lastIndex - index] = (bitLength ushr (index * 8)).toByte()
        }

        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()
        val words = IntArray(80)

        for (offset in padded.indices step 64) {
            for (index in 0 until 16) {
                val byteOffset = offset + index * 4
                words[index] =
                    ((padded[byteOffset].toInt() and 0xff) shl 24) or
                    ((padded[byteOffset + 1].toInt() and 0xff) shl 16) or
                    ((padded[byteOffset + 2].toInt() and 0xff) shl 8) or
                    (padded[byteOffset + 3].toInt() and 0xff)
            }
            for (index in 16 until 80) {
                words[index] = rotateLeft(
                    words[index - 3] xor words[index - 8] xor words[index - 14] xor words[index - 16],
                    1,
                )
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            for (index in 0 until 80) {
                val (f, k) = when (index) {
                    in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                    in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                    in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                    else -> (b xor c xor d) to 0xCA62C1D6.toInt()
                }
                val next = rotateLeft(a, 5) + f + e + k + words[index]
                e = d
                d = c
                c = rotateLeft(b, 30)
                b = a
                a = next
            }
            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
        }

        return intArrayOf(h0, h1, h2, h3, h4).flatMap { value ->
            listOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte(),
            )
        }.toByteArray()
    }

    private fun rotateLeft(value: Int, bits: Int): Int =
        (value shl bits) or (value ushr (32 - bits))
}
