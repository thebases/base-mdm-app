package com.base.launcher.util

import java.io.BufferedInputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.DigestInputStream
import java.security.MessageDigest

object CryptoUtils {
    @JvmStatic
    fun calculateChecksum(fileContent: InputStream): String? {
        val md = try {
            MessageDigest.getInstance("MD5")
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        try {
            BufferedInputStream(fileContent).use { bis ->
                DigestInputStream(bis, md).use { dis ->
                    while (dis.read() != -1) { /* consume */ }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        var hashtext = BigInteger(1, md.digest()).toString(16)
        while (hashtext.length < 32) hashtext = "0$hashtext"
        return hashtext
    }
}
