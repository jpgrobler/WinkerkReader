package za.co.jpsoft.winkerkreader.utils


import za.co.jpsoft.winkerkreader.utils.AppSessionState

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Created by Pieter Grobler on 21/08/2017.
 */
object Installation {
    private const val INSTALLATION = "INSTALLATION"

    @JvmStatic
    @Synchronized
    fun id(id: String, context: Context): Boolean {
        var sID = false
        val key: Int
        if (id.length > 7) {
            key = Character.getNumericValue(id[1]) + Character.getNumericValue(id[4]) * Character.getNumericValue(id[8])
        }
        if (!sID) {
            val installation = File(context.filesDir, INSTALLATION)
            sID = try {
                readInstallationFile(installation, id)
            } catch (e: Exception) {
                false
            }
        }
        return sID
    }

    @JvmStatic
    @Synchronized
    @Throws(IOException::class)
    fun write(id: String, context: Context): Boolean {
        val installation = File(context.filesDir, INSTALLATION)
        try {
            writeInstallationFile(installation, id)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return id(AppSessionState.deviceId, context)
    }

    @Throws(IOException::class)
    private fun readInstallationFile(installation: File, id: String): Boolean {
        RandomAccessFile(installation, "r").use { f ->
            val bytes = ByteArray(f.length().toInt())
            f.readFully(bytes)
            val lid = String(bytes)
            val key = Character.getNumericValue(id[1]) + Character.getNumericValue(id[4]) * Character.getNumericValue(id[8])
            return lid == key.toString()
        }
    }

    @Throws(IOException::class)
    private fun writeInstallationFile(installation: File, id: String) {
        FileOutputStream(installation).use { out ->
            out.write(id.toByteArray())
        }
    }
}