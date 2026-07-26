package za.co.jpsoft.winkerkreader.utils

import android.util.Base64

/**
 * Converts cloud sharing links (Dropbox, OneDrive, Google Drive, SharePoint)
 * into direct-download URLs.
 *
 * Extracted from LaaiDatabasisActivity.processDownloadUrl() and the three
 * private conv()/conv2()/conv3() methods. Pure logic — no Android dependencies
 * except Base64 for the OneDrive encoding.
 *
 * Usage:
 *   val directUrl = CloudUrlTransformer.transform(sharingUrl)
 */
object CloudUrlTransformer {

    /**
     * Detects the cloud service from [url] and applies the appropriate
     * link transformation. Returns [url] unchanged if no rule matches.
     */
    fun transform(url: String): String = when {
        url.contains("www.dropbox.com") -> transformDropbox(url)
        url.contains("1drv.ms") -> transformOneDrive(url)
        url.contains("drive.google.com") -> transformGoogleDrive(url)
        url.contains("sharepoint.com") -> transformSharePoint(url)
        else -> url
    }

    // ── Per-service transforms ────────────────────────────────────────────────

    /**
     * Converts a Dropbox sharing link to a forced-download link by
     * replacing the `dl=0` query parameter with `dl=1`.
     */
    fun transformDropbox(url: String): String =
        url.replace("dl=0", "dl=1")

    /**
     * Converts a OneDrive short sharing URL (1drv.ms) to a direct-download
     * URL via the OneDrive API v1.0 shares endpoint.
     *
     * The sharing URL is base64-encoded and the result is URL-safe encoded
     * (replacing `+` with `-` and `/` with `_`, stripping padding `=`).
     */
    fun transformOneDrive(url: String): String {
        var base64 = Base64.encodeToString(url.toByteArray(), Base64.DEFAULT)
        var encoded = "u!$base64".trim()
        encoded = encoded.replace("=", "")
        encoded = encoded.replace('/', '_')
        encoded = encoded.replace('+', '-')
        return "https://api.onedrive.com/v1.0/shares/$encoded/root/content"
    }

    /**
     * Converts a Google Drive viewing link to a direct-download link.
     * Strips `/view?usp=sharing` and replaces `/file/d/` with `/uc?export=download&id=`.
     */
    fun transformGoogleDrive(url: String): String =
        url.replace("/view?usp=sharing", "")
            .replace("/file/d/", "/uc?export=download&id=")

    /**
     * Converts a SharePoint sharing link to a direct-download URL by
     * removing the existing query string and appending `?download=1`.
     */
    fun transformSharePoint(url: String): String {
        val lastIndex = url.lastIndexOf("?")
        return if (lastIndex < 0) url else url.substring(0, lastIndex) + "?download=1"
    }
}