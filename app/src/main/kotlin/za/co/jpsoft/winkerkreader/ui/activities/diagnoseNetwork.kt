// File: Add this to your activity for network diagnostics
package za.co.jpsoft.winkerkreader.ui.activities

// Add this method to your main activity or fragment

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.Socket

// Add this method to your activity or fragment
private suspend fun diagnoseNetwork(serverIp: String) {
    withContext(Dispatchers.IO) {
        val ports = listOf(49514, 49515, 49516, 49517, 49518, 49519)
        val results = mutableMapOf<Int, Boolean>()

        for (port in ports) {
            try {
                withTimeout(5000) {
                    Socket(serverIp, port).use { socket ->
                        socket.soTimeout = 3000
                        results[port] = true
                        Log.d("Diagnose", "Port $port is OPEN")
                    }
                }
            } catch (e: Exception) {
                results[port] = false
                Log.d("Diagnose", "Port $port is CLOSED: ${e.message}")
            }
        }

        // Log summary
        val openPorts = results.filter { it.value }.keys
        val closedPorts = results.filter { !it.value }.keys
        Log.d("Diagnose", "Open ports: $openPorts")
        Log.d("Diagnose", "Closed ports: $closedPorts")
    }
}

// Call this method from your UI
// lifecycleScope.launch {
//     diagnoseNetwork(settingsManager.serverIp ?: "")
// }