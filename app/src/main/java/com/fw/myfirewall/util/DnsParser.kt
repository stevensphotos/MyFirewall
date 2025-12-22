package com.fw.myfirewall.util

import java.nio.ByteBuffer

object DnsParser {
    fun extractDomain(packet: ByteBuffer): String {
        val originalPosition = packet.position()
        try {
            if (packet.limit() < 40) return "" // IP(20) + UDP(8) + DNS Header(12)
            
            val firstByte = packet.get(0).toInt()
            val ipVersion = (firstByte shr 4) and 0x0F
            val ipHeaderLen = if (ipVersion == 4) (firstByte and 0x0F) * 4 else 40
            val dnsHeaderStart = ipHeaderLen + 8
            
            if (packet.limit() < dnsHeaderStart + 12) return ""

            packet.position(dnsHeaderStart + 12)
            val domain = StringBuilder()
            while (packet.hasRemaining()) {
                val len = packet.get().toInt() and 0xFF
                if (len == 0) break
                if ((len and 0xC0) == 0xC0) break 

                for (i in 0 until len) {
                    if (packet.hasRemaining()) {
                        domain.append(packet.get().toInt().toChar())
                    }
                }
                if (packet.hasRemaining()) {
                    val nextLen = packet.get(packet.position()).toInt() and 0xFF
                    if (nextLen != 0) domain.append(".")
                }
            }
            return domain.toString().lowercase().trim()
        } catch (e: Exception) {
            return ""
        } finally {
            packet.position(originalPosition)
        }
    }

    fun createNxDomainResponse(query: ByteBuffer, readBytes: Int): ByteArray {
        val response = ByteArray(readBytes)
        query.rewind()
        query.get(response, 0, readBytes)

        val firstByte = response[0].toInt()
        val ipHeaderLen = (firstByte and 0x0F) * 4
        val dnsOffset = ipHeaderLen + 8

        // Swap IPs
        for (i in 0..3) {
            val tmp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = tmp
        }

        // Swap Ports
        val tmpPort0 = response[ipHeaderLen]
        val tmpPort1 = response[ipHeaderLen + 1]
        response[ipHeaderLen] = response[ipHeaderLen + 2]
        response[ipHeaderLen + 1] = response[ipHeaderLen + 3]
        response[ipHeaderLen + 2] = tmpPort0
        response[ipHeaderLen + 3] = tmpPort1

        // Set DNS Flags to NXDOMAIN (0x8183)
        if (response.size > dnsOffset + 3) {
            response[dnsOffset + 2] = 0x81.toByte()
            response[dnsOffset + 3] = 0x83.toByte()
        }

        calculateChecksum(response, ipHeaderLen)
        return response
    }

    fun createResponsePacket(queryPacket: ByteBuffer, dnsPayload: ByteArray): ByteArray {
        queryPacket.rewind()
        val firstByte = queryPacket.get(0).toInt()
        val ipHeaderLen = (firstByte and 0x0F) * 4
        val totalLen = ipHeaderLen + 8 + dnsPayload.size
        
        val response = ByteArray(totalLen)
        queryPacket.get(response, 0, ipHeaderLen) // Copy IP Header

        // Swap IPs
        for (i in 0..3) {
            val tmp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = tmp
        }
        
        // Update IP Length
        response[2] = (totalLen shr 8).toByte()
        response[3] = (totalLen and 0xFF).toByte()

        // UDP Header
        response[ipHeaderLen] = queryPacket.get(ipHeaderLen + 2) // Swap ports
        response[ipHeaderLen + 1] = queryPacket.get(ipHeaderLen + 3)
        response[ipHeaderLen + 2] = queryPacket.get(ipHeaderLen)
        response[ipHeaderLen + 3] = queryPacket.get(ipHeaderLen + 1)
        
        val udpLen = 8 + dnsPayload.size
        response[ipHeaderLen + 4] = (udpLen shr 8).toByte()
        response[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()
        response[ipHeaderLen + 6] = 0 // UDP Checksum optional in IPv4
        response[ipHeaderLen + 7] = 0

        System.arraycopy(dnsPayload, 0, response, ipHeaderLen + 8, dnsPayload.size)
        calculateChecksum(response, ipHeaderLen)
        return response
    }

    private fun calculateChecksum(buf: ByteArray, headerLen: Int) {
        buf[10] = 0
        buf[11] = 0
        var sum = 0
        for (i in 0 until headerLen step 2) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        sum = sum.inv()
        buf[10] = (sum shr 8).toByte()
        buf[11] = (sum and 0xFF).toByte()
    }
}
