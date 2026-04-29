package za.co.jpsoft.winkerkreader.utils

import android.content.Context
import android.widget.ImageView
import java.io.File
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract

object PhotoHelper {

    /**
     * Returns the absolute path of a synced photo for the given member GUID,
     * or null if the photo does not exist.
     *
     * Photos are stored in the app's external files directory under "photos/".
     */
    fun getSyncedPhotoPath(context: Context, guid: String?): String? {
        if (guid.isNullOrEmpty()) return null
        val fotoDir = WinkerkContract.winkerkEntry.getFotoDir(context)
        val syncedFile = File(fotoDir, "$guid.jpg")
        return if (syncedFile.exists()) syncedFile.absolutePath else null
    }

    /**
     * Sets a default gender‑based image (male/female) on an ImageView.
     * Also resizes the view to the given DP size (default 48dp).
     */
    fun setDefaultGenderImage(imageView: ImageView, gender: String, sizeDp: Int = 48) {
        val scale = imageView.context.resources.displayMetrics.density
        val pixels = (sizeDp * scale + 0.5f).toInt()
        imageView.layoutParams.height = pixels
        imageView.layoutParams.width = pixels
        imageView.requestLayout()

        val resId = if (gender == "Manlik") R.drawable.kman else R.drawable.kvrou
        imageView.setImageResource(resId)
    }

    /**
     * Sets a generic default image (kontaks) on an ImageView.
     * Used when no photo is available and gender is unknown.
     */
    fun setDefaultGenericImage(imageView: ImageView, sizeDp: Int = 50) {
        val scale = imageView.context.resources.displayMetrics.density
        val pixels = (sizeDp * scale + 0.5f).toInt()
        imageView.layoutParams.height = pixels
        imageView.layoutParams.width = pixels
        imageView.requestLayout()
        imageView.setImageResource(R.drawable.kontaks)
        imageView.tag = "default"
    }
}