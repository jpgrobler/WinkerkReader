package za.co.jpsoft.winkerkreader.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabase
import za.co.jpsoft.winkerkreader.data.pastoral.PastoralDatabaseInitializer
import za.co.jpsoft.winkerkreader.data.pastoral.setup.PastoralDemoDataSeeder

/**
 * One-time worker that seeds pastoral demo notes/reminders a few seconds after
 * cold start, once the initial burst of ContentProvider/paging queries has
 * settled — avoids the connection contention that seeding synchronously during
 * DatabaseInitializer's cold-start flow caused.
 */
@HiltWorker
class PastoralDemoSeedWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val pastoralDemoDataSeeder: PastoralDemoDataSeeder
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val pastoralDb = PastoralDatabase.getInstance(applicationContext)
            PastoralDatabaseInitializer(applicationContext).seedIfEmpty(pastoralDb)
            pastoralDemoDataSeeder.seedIfNeeded(applicationContext)
            Result.success()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Pastoral demo seed failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "PastoralDemoSeedWorker"
    }
}