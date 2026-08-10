package za.co.jpsoft.winkerkreader.utils// utils/AssetPhotoCopier.kt
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.members.provider.WinkerkContract
import za.co.jpsoft.winkerkreader.utils.prefs.SyncPrefs
import java.io.File
import java.io.FileOutputStream

object AssetPhotoCopier {

    private const val TAG = "AssetPhotoCopier"
    private const val ASSET_FOLDER = "fotos"

    suspend fun copyPhotosIfNeeded(
        context: Context,
        syncPrefs: SyncPrefs
    ) {
        if (syncPrefs.photosCopiedFromAssets) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Photos already copied from assets")
            return
        }
        if (BuildConfig.DEBUG) Log.d("AssetPhotoCopier", "Starting photo copy")
        withContext(Dispatchers.IO) {
            try {
                val assetManager = context.assets
                val photoDir = File(WinkerkContract.winkerkEntry.getFotoDir(context))
                if (!photoDir.exists()) {
                    photoDir.mkdirs()
                }

                // List all files in the asset subfolder
                val assetFiles = assetManager.list(ASSET_FOLDER) ?: emptyArray()
                if (assetFiles.isEmpty()) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "No photos found in assets/$ASSET_FOLDER")
                    syncPrefs.photosCopiedFromAssets = true
                    return@withContext
                }

                var copiedCount = 0
                for (fileName in assetFiles) {
                    try {
                        val destFile = File(photoDir, fileName)
                        // Skip if file already exists (preserve user‑added photos)
                        if (destFile.exists()) continue

                        assetManager.open("$ASSET_FOLDER/$fileName").use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        copiedCount++
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e(TAG, "Failed to copy $fileName", e)
                    }
                }

                if (BuildConfig.DEBUG) Log.d(TAG, "Copied $copiedCount photos from assets")
                syncPrefs.photosCopiedFromAssets = true
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Failed to copy photos from assets", e)
                // Do not set the flag; retry next launch
            }
        }
        if (BuildConfig.DEBUG) Log.d("AssetPhotoCopier", "Finished photo copy")
    }
}