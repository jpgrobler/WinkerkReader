package za.co.jpsoft.winkerkreader.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object DatabaseInitializer {
    private const val TAG = "DatabaseInitializer"
    private const val PREF_DB_INITIALIZED = "db_initialized"

    interface ProgressListener {
        fun onProgressUpdate(progress: Int)
        fun onInitializationComplete(success: Boolean)
    }

    /**
     * Initialize the database on first app launch.
     * This performs heavy IO and should be called from a background thread.
     */
    fun initializeDatabase(context: Context, listener: ProgressListener? = null) {
        val settingsManager = SettingsManager.getInstance(context)
        
        // Check if we've already initialized
        if (settingsManager.isDatabaseInitialized()) {
            Log.d(TAG, "Database already initialized")
            listener?.onInitializationComplete(true)
            return
        }

        Log.d(TAG, "First launch - initializing database from assets")

        try {
            // Copy from assets if it doesn't exist
            if (manualCopyDatabase(context, listener)) {
                settingsManager.setDatabaseInitialized(true)
                Log.d(TAG, "Database initialized successfully")
                listener?.onInitializationComplete(true)
            } else {
                Log.e(TAG, "Failed to copy database from assets")
                listener?.onInitializationComplete(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during database initialization", e)
            listener?.onInitializationComplete(false)
        }
    }

    // Add this method to DatabaseInitializer
    fun manualCopyDatabase(context: Context, listener: ProgressListener? = null): Boolean {
        val dbName = WinkerkContract.winkerkEntry.WINKERK_DB
        val dbPath = context.applicationInfo.dataDir + "/databases/" + dbName
        val dbFile = File(dbPath)

        // If database already exists, return true
        if (dbFile.exists() && dbFile.length() > 0) {
            Log.d(TAG, "Database already exists at: $dbPath")
            return true
        }

        // Create databases directory if needed
        dbFile.parentFile?.mkdirs()

        return try {
            // Copy from assets/databases/
            val inputStream = context.assets.open("databases/$dbName")
            val totalSize = inputStream.available().toLong()
            val outputStream = FileOutputStream(dbFile)

            val buffer = ByteArray(8192)
            var length: Int
            var totalRead = 0L
            
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
                totalRead += length
                if (totalSize > 0) {
                    val progress = ((totalRead * 100) / totalSize).toInt()
                    listener?.onProgressUpdate(progress)
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Log.d(TAG, "Database manually copied successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to manually copy database", e)
            false
        }
    }

    fun debugAssetDatabase(context: Context) {
        try {
            Log.d("DB_DEBUG", "=== STARTING DATABASE DEBUG ===")

            // 1. List all assets in root
            val rootAssets = context.assets.list("")
            Log.d("DB_DEBUG", "Root assets: ${rootAssets?.joinToString() ?: "null"}")

            // 2. Check if 'databases' folder exists
            val databasesFolder = context.assets.list("databases")
            Log.d("DB_DEBUG", "databases folder exists: ${databasesFolder != null}")

            if (databasesFolder != null && databasesFolder.isNotEmpty()) {
                Log.d("DB_DEBUG", "Files in databases folder: ${databasesFolder.joinToString()}")

                // Check each file
                for (fileName in databasesFolder) {
                    Log.d("DB_DEBUG", "  - Found: $fileName")
                    val inputStream = context.assets.open("databases/$fileName")
                    Log.d("DB_DEBUG", "    Size: ${inputStream.available()} bytes")
                    inputStream.close()
                }
            } else {
                // Try alternative paths
                val altPaths = listOf("assets/databases", "database", "db", "")
                for (path in altPaths) {
                    val list = if (path.isEmpty()) context.assets.list("")
                    else context.assets.list(path)
                    if (list != null && list.isNotEmpty()) {
                        Log.d("DB_DEBUG", "Found files in '$path': ${list.joinToString()}")
                    }
                }
            }

            // 3. Check where the app expects the database
            val expectedDbPath = context.applicationInfo.dataDir + "/databases/WinkerkReader.sqlite"
            val expectedFile = File(expectedDbPath)
            Log.d("DB_DEBUG", "Expected DB path: $expectedDbPath")
            Log.d("DB_DEBUG", "Expected DB exists: ${expectedFile.exists()}")
            if (expectedFile.exists()) {
                Log.d("DB_DEBUG", "Expected DB size: ${expectedFile.length()} bytes")
            }

            // 4. Try manual copy with exact filename
            val exactDbName = "WinkerkReader.sqlite"  // As specified in your error
            try {
                val inputStream = context.assets.open("databases/$exactDbName")
                Log.d("DB_DEBUG", "SUCCESS: Found database at databases/$exactDbName")
                inputStream.close()
            } catch (e: IOException) {
                Log.e("DB_DEBUG", "FAILED: Cannot find databases/$exactDbName", e)

                // Try lowercase version
                try {
                    val lowerStream = context.assets.open("databases/winkerkreader.sqlite")
                    Log.d("DB_DEBUG", "Found at databases/winkerkreader.sqlite (lowercase)")
                    lowerStream.close()
                } catch (e2: IOException) {
                    Log.e("DB_DEBUG", "Also not found at databases/winkerkreader.sqlite", e2)
                }
            }

        } catch (e: Exception) {
            Log.e("DB_DEBUG", "Error in debug", e)
        }
    }
    private fun isDatabasePopulated(db: SQLiteDatabase): Boolean {
        return try {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM Members", null)
            val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
            cursor.close()
            count > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking database population", e)
            false
        }
    }

    /**
     * Check if database file exists and is valid
     */
    fun isDatabaseExists(context: Context): Boolean {
        val dbFile = File(context.applicationInfo.dataDir + "/databases/" + WinkerkContract.winkerkEntry.WINKERK_DB)
        return dbFile.exists() && dbFile.length() > 0
    }
}