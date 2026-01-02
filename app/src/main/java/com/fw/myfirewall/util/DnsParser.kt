package com.fw.myfirewall.util

import java.nio.ByteBuffer

/**
 * ############################################################################
 * UTIL: DnsParser
 * Rôle : Analyseur et forgeron de paquets DNS pour le moteur de filtrage VPN.
 * Fichiers connectés : MyVpnService.kt (Interception et Proxy DNS).
 * ############################################################################
 */
object DnsParser {

    /**
     * ROUTINE : extractDomain
     * Fonction : Analyse un paquet IP/UDP brut pour en extraire le domaine DNS.
     * [ENTRÉE] packet : ByteBuffer (Paquet brut intercepté dans MyVpnService.startPacketForwarding).
     * [SORTIE] String : Nom de domaine en minuscules (ex: "google.com") pour filtrage.
     */
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

            // Parsing des labels DNS (Format : [longueur][label]...[0])
            while (packet.hasRemaining()) {
                val len = packet.get().toInt() and 0xFF
                if (len == 0) break
                // Gestion de la compression DNS (Pointeurs 0xC0) -> On arrête l'extraction simple
                if ((len and 0xC0) == 0xC0) break

                for (i in 0 until len) {
                    if (packet.hasRemaining()) {
                        domain.append(packet.get().toInt().toChar())
                    }
                }
                if (packet.hasRemaining()) {
                    // Vérifie s'il reste un label pour ajouter un point séparateur
                    val nextLen = packet.get(packet.position()).toInt() and 0xFF
                    if (nextLen != 0) domain.append(".")
                }
            }
            return domain.toString().lowercase().trim()
        } catch (e: Exception) {
            return ""
        } finally {
            // Restauration de la position du buffer pour ne pas corrompre les lectures suivantes
            packet.position(originalPosition)
        }
    }

    /**
     * ROUTINE : createNxDomainResponse
     * Fonction : Forge un paquet de réponse DNS "Non-Existent Domain" pour bloquer une requête.
     * [ENTRÉE] query : ByteBuffer (La requête originale interceptée).
     * [ENTRÉE] readBytes : Taille du paquet original.
     * [SORTIE] ByteArray : Paquet IP/UDP complet prêt à être réinjecté dans le tunnel VPN.
     */
    fun createNxDomainResponse(query: ByteBuffer, readBytes: Int): ByteArray {
        val response = ByteArray(readBytes)
        query.rewind()
        query.get(response, 0, readBytes)

        val firstByte = response[0].toInt()
        val ipHeaderLen = (firstByte and 0x0F) * 4
        val dnsOffset = ipHeaderLen + 8

        // INVERSION IPs : Source devient Destination et vice-versa
        for (i in 0..3) {
            val tmp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = tmp
        }

        // INVERSION PORTS UDP : Source (Port 53) <=> Destination
        val tmpPort0 = response[ipHeaderLen]
        val tmpPort1 = response[ipHeaderLen + 1]
        response[ipHeaderLen] = response[ipHeaderLen + 2]
        response[ipHeaderLen + 1] = response[ipHeaderLen + 3]
        response[ipHeaderLen + 2] = tmpPort0
        response[ipHeaderLen + 3] = tmpPort1

        // CONFIGURATION FLAGS DNS : Positionnement sur NXDOMAIN (0x8183)
        if (response.size > dnsOffset + 3) {
            response[dnsOffset + 2] = 0x81.toByte()
            response[dnsOffset + 3] = 0x83.toByte()
        }

        // RECALCUL DE L'INTÉGRITÉ
        calculateChecksum(response, ipHeaderLen)
        return response
    }

    /**
     * ROUTINE : createResponsePacket
     * Fonction : Encapsule un payload DNS reçu d'un serveur réel dans un paquet IP/UDP.
     * [ENTRÉE] queryPacket : ByteBuffer (Contexte de la requête originale pour les en-têtes).
     * [ENTRÉE] dnsPayload : ByteArray (Réponse brute reçue du serveur DNS distant).
     * [SORTIE] ByteArray : Paquet reconstitué pour MyVpnService.proxyDnsQuery.
     */
    fun createResponsePacket(queryPacket: ByteBuffer, dnsPayload: ByteArray): ByteArray {
        queryPacket.rewind()
        val firstByte = queryPacket.get(0).toInt()
        val ipHeaderLen = (firstByte and 0x0F) * 4
        val totalLen = ipHeaderLen + 8 + dnsPayload.size
        
        val response = ByteArray(totalLen)
        queryPacket.get(response, 0, ipHeaderLen) // Copie de l'en-tête IP original

        // INVERSION IPs : Redirection du trafic vers l'émetteur original
        for (i in 0..3) {
            val tmp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = tmp
        }
        
        // MISE À JOUR LONGUEUR IP
        response[2] = (totalLen shr 8).toByte()
        response[3] = (totalLen and 0xFF).toByte()

        // EN-TÊTE UDP : Inversion des ports et mise à jour de la taille
        response[ipHeaderLen] = queryPacket.get(ipHeaderLen + 2)
        response[ipHeaderLen + 1] = queryPacket.get(ipHeaderLen + 3)
        response[ipHeaderLen + 2] = queryPacket.get(ipHeaderLen)
        response[ipHeaderLen + 3] = queryPacket.get(ipHeaderLen + 1)
        
        val udpLen = 8 + dnsPayload.size
        response[ipHeaderLen + 4] = (udpLen shr 8).toByte()
        response[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()
        response[ipHeaderLen + 6] = 0 // UDP Checksum optionnel en IPv4
        response[ipHeaderLen + 7] = 0

        // ASSEMBLAGE FINAL
        System.arraycopy(dnsPayload, 0, response, ipHeaderLen + 8, dnsPayload.size)
        calculateChecksum(response, ipHeaderLen)
        return response
    }

    /**
     * ROUTINE : calculateChecksum (Interne)
     * Fonction : Calcule la somme de contrôle de l'en-tête IP (RFC 791).
     * [ENTRÉE] buf : Le paquet complet en cours de modification.
     * [ENTRÉE] headerLen : Longueur de l'en-tête IP.
     */
    private fun calculateChecksum(buf: ByteArray, headerLen: Int) {
        buf[10] = 0 // Reset du champ Checksum existant
        buf[11] = 0
        var sum = 0
        for (i in 0 until headerLen step 2) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        sum = sum.inv() // Complément à un
        buf[10] = (sum shr 8).toByte()
        buf[11] = (sum and 0xFF).toByte()
    }
}
