package za.co.jpsoft.winkerkreader

import android.app.Application
import za.co.jpsoft.winkerkreader.utils.UnifiedCallMonitor

class WinkerkReader : Application() {

    companion object {
        private var unifiedCallMonitor: UnifiedCallMonitor? = null

        fun setUnifiedCallMonitor(monitor: UnifiedCallMonitor?) {
            unifiedCallMonitor = monitor
        }

        fun getUnifiedCallMonitor(): UnifiedCallMonitor? {
            return unifiedCallMonitor
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize any app-wide components
    }
}