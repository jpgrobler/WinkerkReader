package za.co.jpsoft.winkerkreader.utils

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.services.BootForegroundServiceStarter
import za.co.jpsoft.winkerkreader.services.CallMonitoringService
import za.co.jpsoft.winkerkreader.services.ServiceKeepAlive
import za.co.jpsoft.winkerkreader.services.WhatsAppNotificationService

object ServiceUtils {

    private const val TAG = "ServiceUtils"

    /**
     * Check if a specific service is running
     * Uses the service's own isRunning() method if available
     */
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        return try {
            when (serviceClass) {
                WhatsAppNotificationService::class.java -> {
                    WhatsAppNotificationService.isRunning()
                }

                CallMonitoringService::class.java -> {
                    CallMonitoringService.isRunning()
                }

                ServiceKeepAlive::class.java -> {
                    ServiceKeepAlive.isRunning()
                }

                BootForegroundServiceStarter::class.java -> {
                    // Boot service doesn't have isRunning, use ActivityManager
                    checkServiceWithActivityManager(context, serviceClass)
                }

                else -> {
                    checkServiceWithActivityManager(context, serviceClass)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if service is running", e)
            false
        }
    }

    private fun checkServiceWithActivityManager(context: Context, serviceClass: Class<*>): Boolean {
        try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager == null) {
                Log.e(TAG, "ActivityManager is null")
                return false
            }

            val runningServices = activityManager.getRunningServices(Int.MAX_VALUE)

            return runningServices?.any { service ->
                service.service.className == serviceClass.name
            } ?: false

        } catch (e: Exception) {
            Log.e(TAG, "Error checking service with ActivityManager", e)
            return false
        }
    }

    /**
     * Check if any service in a list is running
     */
    fun isAnyServiceRunning(context: Context, serviceClasses: List<Class<*>>): Boolean {
        return serviceClasses.any { isServiceRunning(context, it) }
    }

    /**
     * Start a service only if it's not already running
     */
    fun startServiceIfNotRunning(context: Context, serviceClass: Class<*>, action: String? = null) {
        try {
            if (isServiceRunning(context, serviceClass)) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "✅ Service ${serviceClass.simpleName} already running, skipping start"
                    )
                }
                return
            }

            val intent = android.content.Intent(context, serviceClass)
            action?.let { intent.action = it }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "🚀 Started service ${serviceClass.simpleName}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting service ${serviceClass.simpleName}", e)
        }
    }

    /**
     * Stop a service if it's running
     */
    fun stopServiceIfRunning(context: Context, serviceClass: Class<*>) {
        try {
            if (isServiceRunning(context, serviceClass)) {
                val intent = android.content.Intent(context, serviceClass)
                context.stopService(intent)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "🛑 Stopped service ${serviceClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service ${serviceClass.simpleName}", e)
        }
    }

    /**
     * Get all running services as a list of strings (for debugging)
     */
    fun getRunningServiceNames(context: Context): List<String> {
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager == null) return emptyList()

            val runningServices = activityManager.getRunningServices(Int.MAX_VALUE)
            runningServices?.mapNotNull { service ->
                service.service.className
            } ?: emptyList()

        } catch (e: Exception) {
            Log.e(TAG, "Error getting running services", e)
            emptyList()
        }
    }

    /**
     * Check if any of our app's services are running
     */
    fun areOurServicesRunning(context: Context): Boolean {
        val ourServices = listOf(
            WhatsAppNotificationService::class.java,
            CallMonitoringService::class.java,
            ServiceKeepAlive::class.java,
            BootForegroundServiceStarter::class.java
        )
        return isAnyServiceRunning(context, ourServices)
    }

    /**
     * Debug helper - log all running services
     */
    fun logRunningServices(context: Context) {
        if (BuildConfig.DEBUG) {
            val services = getRunningServiceNames(context)
            Log.d(TAG, "📋 Running services (${services.size}):")
            services.forEach { serviceName ->
                if (serviceName.contains("winkerkreader", ignoreCase = true)) {
                    Log.d(TAG, "  - $serviceName ✅")
                }
            }
        }
    }
}