package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.ContentUris
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.databinding.LidmaatDetailBinding
import za.co.jpsoft.winkerkreader.utils.PhotoHelper
import za.co.jpsoft.winkerkreader.utils.forceShowIcons
import java.io.File
import java.io.FileOutputStream

/**
 * Owns all member-photo handling for [za.co.jpsoft.winkerkreader.ui.activities.LidmaatDetailActivity]:
 * picking from the gallery, taking a new photo, saving/copying it into app
 * storage, persisting the reference to the member record, and displaying the
 * current photo (or the default placeholder) via Glide.
 *
 * Extracted from LidmaatDetailActivity.kt — behaviour is unchanged from the
 * original inline implementation.
 *
 * IMPORTANT: must be constructed at field-initialisation time in the host
 * Activity (before super.onCreate()), because [registerForActivityResult]
 * must be called before the Activity reaches the STARTED state — same
 * requirement as [ActivityResultCoordinator].
 *
 * @param getMemberGuid  returns the currently-displayed member's GUID
 * @param getCurrentId   returns the currently-displayed member's row id (for the ContentProvider update)
 */
class MemberPhotoController(
    private val activity: AppCompatActivity,
    private val binding: LidmaatDetailBinding,
    private val getMemberGuid: () -> String?,
    private val getCurrentId: () -> Int
) {
    private companion object {
        private const val TAG = "MemberPhotoController"
    }

    private var mImageUri: Uri? = null

    /**
     * Bridges the host Activity's own process-death save/restore of the
     * in-flight camera capture URI into this controller. The Activity should
     * set this from its restored `savedInstanceState` right after
     * constructing the controller — the camera result callback below reads
     * the controller's own [mImageUri], so without this bridge a restored
     * URI in the Activity would never actually reach the callback that needs it.
     */
    var pendingImageUri: Uri?
        get() = mImageUri
        set(value) {
            mImageUri = value
        }

    private val photoPickerLauncher =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { processSelectedImage(it) }
        }

    private val takePictureLauncher =
        activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = mImageUri
            if (success && uri != null) {
                processSelectedImage(uri)
            } else {
                if (uri == null) {
                    if (BuildConfig.DEBUG) Log.e(
                        TAG,
                        "Camera returned but image URI is null (activity state lost)"
                    )
                    Toast.makeText(activity, "Camera error: lost image URI", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            // Clear the temporary URI to avoid re-use
            mImageUri = null
        }

    /** Shows the "take photo / choose from gallery" popup, anchored to the photo view. */
    fun showImagePopup() {
        val popup = PopupMenu(activity, binding.detailKontakFoto)
        popup.menuInflater.inflate(R.menu.image_popup, popup.menu)
        popup.menu.findItem(R.id.whatsapp_foto).isVisible = false

        popup.forceShowIcons()
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.kamera_foto -> {
                    kamera()
                    true
                }

                R.id.gallery_foto -> {
                    openImageChooser()
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    /** Loads and displays the member's synced photo, falling back to the default placeholder. */
    fun loadMemberPhoto(guid: String?) {
        if (guid.isNullOrEmpty()) {
            setDefaultPhoto()
            return
        }

        // Directly use PhotoHelper – it returns the full path if file exists, else null
        val photoPath = PhotoHelper.getSyncedPhotoPath(activity, guid)
        if (photoPath != null) {
            val file = File(photoPath)
            if (file.exists()) {
                val pixels = photoPixels(200)
                binding.detailKontakFoto.layoutParams.height = pixels
                binding.detailKontakFoto.layoutParams.width = pixels
                binding.detailKontakFoto.requestLayout()

                Glide.with(activity)
                    .load(file)
                    .override(pixels, pixels)
                    .centerCrop()
                    .placeholder(R.drawable.kontak)
                    .error(R.drawable.kontak)
                    .into(binding.detailKontakFoto)

                binding.detailKontakFoto.tag = "synced"
                return
            }
        }

        // Fallback: default photo
        setDefaultPhoto()
    }

    private fun setDefaultPhoto() {
        val pixels = photoPixels(50)
        binding.detailKontakFoto.layoutParams.height = pixels
        binding.detailKontakFoto.layoutParams.width = pixels
        binding.detailKontakFoto.requestLayout()
        binding.detailKontakFoto.setImageResource(R.drawable.kontak)
        binding.detailKontakFoto.tag = "default"
    }

    private fun openImageChooser() {
        // Launch Photo Picker – no need to request storage permissions
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun processSelectedImage(imageUri: Uri) {
        val guid = getMemberGuid()
        val newPath = copyFoto(imageUri, guid)
        if (newPath.isEmpty()) {
            Toast.makeText(activity, "Failed to save image", Toast.LENGTH_SHORT).show()
            return
        }

        // Update UI to show the new photo using Glide
        val pixels = photoPixels(200)
        binding.detailKontakFoto.layoutParams.height = pixels
        binding.detailKontakFoto.layoutParams.width = pixels
        binding.detailKontakFoto.requestLayout()

        val photoFile = File(activity.getExternalFilesDir(null), "photos/$newPath")
        Glide.with(activity)
            .load(photoFile)
            .override(pixels, pixels)
            .centerCrop()
            .placeholder(R.drawable.kontak)
            .error(R.drawable.kontak)
            .into(binding.detailKontakFoto)

        binding.detailKontakFoto.tag = "synced"

        // Save reference in database
        val id = getCurrentId()
        val memberValues = ContentValues().apply { put(winkerkEntry.LIDMATE_PICTUREPATH, newPath) }
        val memberUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, id.toLong())
        activity.contentResolver.update(
            memberUri,
            memberValues,
            "${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ?",
            arrayOf(id.toString())
        )
    }

    private fun copyFoto(imageUri: Uri, guid: String?): String {
        if (guid.isNullOrEmpty()) return ""

        // Decode with inSampleSize=2 to reduce memory for thumbnail creation
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val fullBitmap = activity.contentResolver.openInputStream(imageUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        } ?: return ""

        // Thumbnail creation
        val width = winkerkEntry.THUMBSIZE
        val height = winkerkEntry.THUMBSIZE
        val thumbBitmap = ThumbnailUtils.extractThumbnail(fullBitmap, width, height)
        thumbBitmap.recycle()

        // Save full-size image to external directory (use original quality)
        val externalDir = activity.getExternalFilesDir(null)
        if (externalDir != null) {
            val photoDir = File(externalDir, "Fotos")
            if (!photoDir.exists()) photoDir.mkdirs()
            val photoFile = File(photoDir, "$guid.jpg")
            if (photoFile.exists()) photoFile.delete()
            activity.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                FileOutputStream(photoFile).use { out ->
                    inputStream.copyTo(out)   // Copy file directly, no decode/encode
                }
            }
        }
        fullBitmap.recycle()
        return "$guid.jpg"
    }

    private fun kamera() {
        try {
            val photo = createTemporaryFile("picture", ".jpg")
            photo.delete()
            val imageUri =
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", photo)
            mImageUri = imageUri
            takePictureLauncher.launch(imageUri)
        } catch (e: Exception) {
            Toast.makeText(activity, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Throws(Exception::class)
    private fun createTemporaryFile(part: String, ext: String): File {
        val tempDir = File("${winkerkEntry.getFotoDir(activity)}/.temp/")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return File.createTempFile(part, ext, tempDir)
    }

    private fun photoPixels(dp: Int): Int {
        val density = activity.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}