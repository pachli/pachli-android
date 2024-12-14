/* Copyright 2022 Tusky contributors
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.util

import android.util.Base64
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

object CryptoUtil {
    const val CURVE_PRIME256_V1 = "prime256v1"

    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    private fun secureRandomBytes(len: Int): ByteArray {
        val ret = ByteArray(len)
        SecureRandom.getInstance("SHA1PRNG").nextBytes(ret)
        return ret
    }

    fun secureRandomBytesEncoded(len: Int): String {
        return Base64.encodeToString(secureRandomBytes(len), BASE64_FLAGS)
    }

    data class EncodedKeyPair(val pubkey: String, val privKey: String)

    fun generateECKeyPair(curve: String): EncodedKeyPair {
        val spec = ECGenParameterSpec(curve)
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(spec)
        val keyPair = gen.genKeyPair()
        val pubKey = keyPair.public as ECPublicKey
        val privKey = keyPair.private
        val encodedPubKey = Base64.encodeToString(encodeP256Dh(pubKey), BASE64_FLAGS)
        val encodedPrivKey = Base64.encodeToString(privKey.encoded, BASE64_FLAGS)
        return EncodedKeyPair(encodedPubKey, encodedPrivKey)
    }

    /**
     * Encodes the public key point P in X9.62 uncompressed format.
     *
     * This is section "2.3.3 Elliptic-Curve-Point-to-Octet-String Conversion" from
     * https://www.secg.org/sec1-v2.pdf
     */
    // Code originally from https://github.com/tateisu/SubwayTooter/blob/main/base/src/main/java/jp/juggler/crypt/CryptUtils.kt
    // under the Apache 2.0 license
    private fun encodeP256Dh(key: ECPublicKey): ByteArray = key.run {
        val bitsInByte = 8
        val fieldSizeBytes = (params.order.bitLength() + bitsInByte - 1) / bitsInByte
        return ByteArray(1 + 2 * fieldSizeBytes).also { dst ->
            var offset = 0
            dst[offset++] = 0x04 // 0x04 designates uncompressed format
            w.affineX.toByteArray().let { x ->
                when {
                    x.size <= fieldSizeBytes ->
                        System.arraycopy(x, 0, dst, offset + fieldSizeBytes - x.size, x.size)

                    x.size == fieldSizeBytes + 1 && x[0].toInt() == 0 ->
                        System.arraycopy(x, 1, dst, offset, fieldSizeBytes)

                    else -> error("x value is too large")
                }
            }
            offset += fieldSizeBytes
            w.affineY.toByteArray().let { y ->
                when {
                    y.size <= fieldSizeBytes ->
                        System.arraycopy(y, 0, dst, offset + fieldSizeBytes - y.size, y.size)

                    y.size == fieldSizeBytes + 1 && y[0].toInt() == 0 ->
                        System.arraycopy(y, 1, dst, offset, fieldSizeBytes)

                    else -> error("y value is too large")
                }
            }
        }
    }
}
