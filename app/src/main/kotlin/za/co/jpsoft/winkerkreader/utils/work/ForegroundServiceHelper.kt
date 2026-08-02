// ForegroundServiceHelper.kt
package za.co.jpsoft.winkerkreader.utils.work

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build

object ForegroundServiceHelper {

    fun startForeground(
        service: Service,
        id: Int,
        notification: Notification,
        type: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && type != ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE) {
            service.startForeground(id, notification, type)
        } else {
            service.startForeground(id, notification)
        }
    }
}

// ForegroundServiceType.kt - Use correct constants
object ForegroundServiceType {
    const val NONE = 0
    const val DATA_SYNC = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC  // 1
    const val MEDIA_PLAYBACK = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK  // 2
    const val PHONE_CALL = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL  // 4 (not 'phone')
    const val LOCATION = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION  // 8
    const val CONNECTED_DEVICE = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE  // 16
    const val MICROPHONE = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE  // 128
    const val CAMERA = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA  // 64
    const val SYSTEM_EXEMPTED = ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED  // 1024
}