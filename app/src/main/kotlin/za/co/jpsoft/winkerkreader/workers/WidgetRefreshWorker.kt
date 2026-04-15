package za.co.jpsoft.winkerkreader.workers

import za.co.jpsoft.winkerkreader.widget.WinkerkReaderWidgetProvider

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "widget_refresh_work"
    }

    override suspend fun doWork(): Result {
        return try {
            refreshWidgets()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun refreshWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val componentName = ComponentName(applicationContext, WinkerkReaderWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)

        val intent = android.content.Intent(applicationContext, WinkerkReaderWidgetProvider::class.java).apply {
            action = "android.appwidget.action.APPWIDGET_UPDATE"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        applicationContext.sendBroadcast(intent)
    }
}