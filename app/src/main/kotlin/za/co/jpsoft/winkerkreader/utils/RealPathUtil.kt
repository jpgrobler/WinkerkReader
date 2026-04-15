package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore

/**
 * Utility to obtain the real file path from a content URI.
 */
object RealPathUtil {

    /**
     * Get real path from URI for API 19 and above.
     *
     * @param context Application context
     * @param uri     Content URI with document ID
     * @return Real file path, or empty string if not found
     */
    @JvmStatic
    fun getRealPathFromURI_API19(context: Context, uri: Uri): String {
        val documentId = DocumentsContract.getDocumentId(uri)
        // Split at colon and take the second part (the actual ID)
        val id = documentId.split(":").getOrNull(1) ?: return ""

        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val selection = "${MediaStore.Images.Media._ID} = ?"
        val selectionArgs = arrayOf(id)

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(projection[0])
                return cursor.getString(columnIndex)
            }
        }
        return ""
    }
}