package com.fw.myfirewall.util

import android.util.Log
import java.nio.ByteBuffer

object DnsParser {
    /**
     * Extrait le nom de domaine d'un paquet DNS (UDP Port 53).
     * @param packet Le buffer contenant l'intégralité du paquet IP.
     */
    fun extractDomain(packet: ByteBuffer): String {
        val previousPosition = packet.position()
        try {
            // 1. Positionnement au début de la section Question du DNS
            // Offset 28 = IP Header (20) + UDP Header (8)
            // Offset 12 = DNS Header (ID, Flags, counts...)
            packet.position(28 + 12)

            val domainResult = StringBuilder()

            // 2. Lecture des labels DNS (format : [taille][texte][taille][texte]...[0])
            var labelLength = packet.get().toInt() and 0xFF

            while (labelLength > 0) {
                for (i in 0 until labelLength) {
                    domainResult.append(packet.get().toInt().toChar())
                }

                labelLength = packet.get().toInt() and 0xFF
                if (labelLength > 0) {
                    domainResult.append(".")
                }
            }

            val finalDomain = domainResult.toString().lowercase()

            // Log de debug pour vérifier l'extraction dans Logcat
            Log.d("VpnDebug", "Domaine extrait : '$finalDomain'")

            return finalDomain

        } catch (e: Exception) {
            Log.e("DnsParser", "Erreur lors de l'extraction DNS", e)
            return ""
        } finally {
            // Toujours remettre le buffer à sa position initiale pour ne pas corrompre
            // la suite du traitement du paquet dans la boucle principale.
            packet.position(previousPosition)
        }
    }
}
