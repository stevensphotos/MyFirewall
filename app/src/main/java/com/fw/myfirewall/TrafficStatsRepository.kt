package com.fw.myfirewall

import android.content.Context
import android.net.TrafficStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Add these variables to your TrafficStatsRepository class
class TrafficStatsRepository(private val context: Context) {
    // ... existing total counters ...

    // Speed flows (Bytes per second)
    private val _currentDownloadSpeed = MutableStateFlow(0L)
    val currentDownloadSpeed = _currentDownloadSpeed.asStateFlow()

    private val _currentUploadSpeed = MutableStateFlow(0L)
    val currentUploadSpeed = _currentUploadSpeed.asStateFlow()

    private var lastRxTotal = 0L
    private var lastTxTotal = 0L
    private var lastUpdateTime = System.currentTimeMillis()

    // Call this function periodically (e.g., every 1 second) from a Coroutine in ViewModel or Service
    fun updateSpeed() {
        val currentRx = TrafficStats.getTotalRxBytes()
        val currentTx = TrafficStats.getTotalTxBytes()
        val currentTime = System.currentTimeMillis()

        val timeDiff = (currentTime - lastUpdateTime) / 1000.0 // seconds

        if (timeDiff > 0 && lastRxTotal > 0) {
            val rxDiff = currentRx - lastRxTotal
            val txDiff = currentTx - lastTxTotal

            _currentDownloadSpeed.value = (rxDiff / timeDiff).toLong()
            _currentUploadSpeed.value = (txDiff / timeDiff).toLong()
        }

        lastRxTotal = currentRx
        lastTxTotal = currentTx
        lastUpdateTime = currentTime
    }
}