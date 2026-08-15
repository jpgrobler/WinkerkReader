package za.co.jpsoft.winkerkreader

import android.app.Application
import leakcanary.LeakCanary
import shark.AndroidReferenceMatchers
import za.co.jpsoft.winkerkreader.BuildConfig
// Real LeakCanary setup – only used in debug builds
class DebugLeakCanaryHelper : LeakCanaryHelper {
    override fun setup(application: Application) {
        LeakCanary.config = LeakCanary.config.copy(
            referenceMatchers = AndroidReferenceMatchers.appDefaults
//                    + AndroidReferenceMatchers.ignoredInstanceField(
//                        "android.service.notification.NotificationListenerService\$NotificationListenerWrapper",
//                        "this\$0"
//                    )
        )
        // Uncomment if manual install is needed:
        // leakcanary.AppWatcher.manualInstall(application)
    }
}

// This class replaces the main one in debug builds
class DebugWinkerkReader : WinkerkReader() {
    override fun createLeakCanaryHelper(): LeakCanaryHelper {
        return DebugLeakCanaryHelper()
    }
}