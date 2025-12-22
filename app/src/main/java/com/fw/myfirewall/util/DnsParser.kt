package com.fw.myfirewall.util

import android.util.Log
import java.nio.ByteBuffer

object DnsParser {
    fun extractDomain(packet: ByteBuffer): String {
        val originalPosition = packet.position()
        try {
            val firstByte = packet.get(0).toInt()
            val ipVersion = (firstByte shr 4) and 0x0F

            // Determine the start of DNS data (IP Header + UDP Header (8) + DNS Header (12))
            val dnsHeaderStart = if (ipVersion == 4) {
                val ihl = (firstByte and 0x0F) * 4
                ihl + 8
            } else if (ipVersion == 6) {
                40 + 8 // IPv6 fixed header is 40 bytes
            } else 0


            if (packet.limit() < dnsHeaderStart + 12) return ""

            // Move to the start of the Question section (after 12 bytes of DNS header)
            packet.position(dnsHeaderStart + 12)

    val domainBuilder = StringBuilder()

            // 4. Parse Labels
            while (packet.hasRemaining()) {
                val labelLength = packet.get().toInt() and 0xFF
                if (labelLength == 0) break // End of domain name

                // Safety check for compression or malformed packets (DNS compression starts with 0xC0)
                if ((labelLength and 0xC0) == 0xC0) {
                    // We don't handle pointer compression here, usually not needed for simple queries
                    break
                }

                for (i in 0 until labelLength) {
                    if (packet.hasRemaining()) {
                        domainBuilder.append(packet.get().toInt().toChar())
                    }
                }

                // Peek at next byte to see if we should add a dot
                val nextLen = if (packet.hasRemaining()) packet.get(packet.position()).toInt() and 0xFF else 0
                if (nextLen != 0) {
                    domainBuilder.append(".")
                }
            }

            val finalDomain = domainBuilder.toString().lowercase().trim()

            if (finalDomain.isNotEmpty()) {
                Log.d("VpnDebug", "Successfully extracted: $finalDomain")
            }
            return finalDomain

        } catch (e: Exception) {
            Log.e("DnsParser", "DNS Extraction failed: ${e.message}")
            return ""
        } finally {
            // CRITICAL: Always restore position for MyVpnService to read correctly
            packet.position(originalPosition)
        }
    }
}
