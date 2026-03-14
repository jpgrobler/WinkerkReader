package za.co.jpsoft.winkerkreader.data

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ThumbnailUtils
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.joda.time.DateTime
import org.joda.time.Years
import za.co.jpsoft.winkerkreader.MainActivity2
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.Utils.parseDate
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.getIntOrDefault
import za.co.jpsoft.winkerkreader.getStringOrEmpty
import za.co.jpsoft.winkerkreader.PhotoHelper
import java.io.File
import java.lang.ref.WeakReference
import java.text.Normalizer
import java.util.*
import java.util.concurrent.Executors

class WinkerkCursorAdapter(context: Context, cursor: Cursor?) : CursorAdapter(context, cursor, 0) {

    companion object {
        private const val TAG = "Winkerk_CursorAdaptor"
        private const val SECTIONED_STATE = 1
        private const val SECTIONED_STATE2 = 2
        private const val REGULAR_STATE = 3
        private val imageLoaderExecutor = Executors.newSingleThreadExecutor()
    }

    private var mRowStates: IntArray? = null
    private val imageCache: LruCache<String, Bitmap>

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        imageCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
        }
        if (cursor != null) {
            mRowStates = IntArray(cursor.count)
        }
    }

    override fun swapCursor(newCursor: Cursor?): Cursor? {
        val old = super.swapCursor(newCursor)
        notifyDataSetChanged()
        if (newCursor != null) {
            mRowStates = IntArray(newCursor.count)
        }
        return old
    }

    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        Log.v(TAG, "newView")
        val view = if (winkerkEntry.LISTVIEW == 1) {
            LayoutInflater.from(context).inflate(R.layout.list_item, parent, false)
        } else {
            LayoutInflater.from(context).inflate(R.layout.list_item_2, parent, false)
        }
        view.tag = ViewHolder(view)
        return view
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        Log.v(TAG, "bindView")
        try {
            if (cursor.isClosed || cursor.count <= 0) return
            if (winkerkEntry.LOADER == "GEMEENTENAAM" || winkerkEntry.LOADER == "DATADATUM") return

            val vh = view.tag as ViewHolder
            val member = extractMemberData(cursor)

            if (winkerkEntry.SOEKLIST) {
                view.setBackgroundColor(Color.LTGRAY)
            }

            when (member.congregation) {
                winkerkEntry.GEMEENTE_NAAM -> view.setBackgroundColor(winkerkEntry.GEMEENTE_KLEUR)
                winkerkEntry.GEMEENTE2_NAAM -> view.setBackgroundColor(winkerkEntry.GEMEENTE2_KLEUR)
                winkerkEntry.GEMEENTE3_NAAM -> view.setBackgroundColor(winkerkEntry.GEMEENTE3_KLEUR)
            }

            applyVisibilitySettings(vh)
            resetViewState(vh)

            bindPhotoData(vh, member, context)
            bindSelectionState(view, vh, member)
            bindBasicInfo(vh, member)
            bindContactInfo(vh, member)
            bindAgeInfo(vh, member)
            bindWeddingInfo(vh, member)
            bindEmailIndicator(vh, member)

            if (winkerkEntry.SOEKLIST) {
                vh.vanTextView.text = highlight(winkerkEntry.SOEK, vh.vanTextView.text.toString())
                vh.nameTextView.text = highlight(winkerkEntry.SOEK, vh.nameTextView.text.toString())
                vh.cellTextView.text = highlight(winkerkEntry.SOEK, vh.cellTextView.text.toString())
                vh.telTextView.text = highlight(winkerkEntry.SOEK, vh.telTextView.text.toString())
                vh.spearatorTextView.text = highlight(winkerkEntry.SOEK, vh.spearatorTextView.text.toString())
            }

            handleSeparators(view, vh, cursor, member)

        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cursor was closed, refreshing data", e)
        }
    }

    private fun extractMemberData(cursor: Cursor): MemberData {
        val member = MemberData()
        member.name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
        member.surname = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)
        member.gender = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESLAG)
        member.congregation = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEMEENTE)
        member.familyHead = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESINSHOOFGUID)
        member.cellphone = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_SELFOON)
        member.landline = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LANDLYN)
        member.email = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_EPOS)
        member.ward = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_WYK)
        member.address = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_STRAATADRES).takeIf { it.isNotEmpty() } ?: "GEEN"
        member.birthday = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)
        if (member.birthday.isNotEmpty() && member.birthday.length >= 10) {
            try {
                member.birthdayDT = parseDate(member.birthday.substring(0, 10))
                val years = Years.yearsBetween(member.birthdayDT, DateTime.now())
                if (years.years >= 0) member.age = years.years.toString()
            } catch (_: Exception) { }
        }
        member.weddingDate = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_HUWELIKSDATUM)
        if (member.weddingDate.isNotEmpty() && member.weddingDate.length >= 10) {
            try {
                member.weddingDT = parseDate(member.weddingDate.substring(0, 10))
                val years = Years.yearsBetween(member.weddingDT, DateTime.now())
                if (years.years >= 0) member.weddingYears = years.years.toString()
            } catch (_: Exception) { }
        }
        member.picturePath = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_PICTUREPATH)
        member.tag = cursor.getIntOrDefault(winkerkEntry.LIDMATE_TAG, 0)
        member.guid = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATGUID)
        return member
    }

    private fun applyVisibilitySettings(vh: ViewHolder) {
        vh.fotoFrame.visibility = if (winkerkEntry.LIST_FOTO) View.VISIBLE else View.GONE
        vh.ouderdomTextView.visibility = if (winkerkEntry.LIST_OUDERDOM) View.VISIBLE else View.GONE
        vh.wykTextView.visibility = if (winkerkEntry.LIST_WYK) View.VISIBLE else View.GONE
        vh.huwelikTextView.visibility = if (winkerkEntry.LIST_HUWELIKBLOK) View.VISIBLE else View.GONE
        vh.eposImageView.visibility = if (winkerkEntry.LIST_EPOS) View.VISIBLE else View.GONE
        if (winkerkEntry.LIST_VERJAARBLOK) {
            vh.koekImageView.visibility = View.VISIBLE
            vh.verjaarTextView.visibility = View.VISIBLE
        } else {
            vh.koekImageView.visibility = View.GONE
            vh.verjaarTextView.visibility = View.GONE
        }
    }

    private fun resetViewState(vh: ViewHolder) {
        vh.koekImageView.visibility = View.GONE
        vh.selBlock.visibility = View.GONE
        vh.telBlock.visibility = View.GONE
        vh.whatsappImageView.visibility = View.GONE
        vh.huwelikTextView.text = ""
        vh.ringImageView.visibility = View.GONE
        vh.eposImageView.visibility = View.GONE
        vh.wykTextView.visibility = View.GONE
    }

    private fun bindPhotoData(vh: ViewHolder, member: MemberData, context: Context) {
        val scale = context.resources.displayMetrics.density
        var pixels = (30 * scale + 0.5f).toInt()
        var photoPath: String? = null

        if (member.guid.isNotEmpty()) {
            photoPath = PhotoHelper.getSyncedPhotoPath(context, member.guid)
        }

        if (photoPath == null) {
            PhotoHelper.setDefaultGenderImage(vh.fotoImageView, member.gender, if (winkerkEntry.LISTVIEW == 2) 50 else 30)
            return
        }

        if (winkerkEntry.LISTVIEW == 2) {
            pixels = (50 * scale + 0.5f).toInt()
        }
        vh.fotoImageView.layoutParams.height = pixels
        vh.fotoImageView.layoutParams.width = pixels
        vh.fotoImageView.requestLayout()

        val cachedBitmap = imageCache.get(photoPath)
        if (cachedBitmap != null) {
            vh.fotoImageView.setImageBitmap(cachedBitmap)
        } else {
            vh.fotoImageView.tag = photoPath
            imageLoaderExecutor.submit(LoadImage(WeakReference(vh.fotoImageView), imageCache, photoPath))
        }

        if (winkerkEntry.RECORDSTATUS == "2") {
            vh.fotoFrameOverlay.setImageResource(R.drawable.circle_crop_onaktief)
        } else {
            vh.fotoFrameOverlay.setImageResource(R.drawable.circle_crop)
        }
    }

    private fun bindSelectionState(view: View, vh: ViewHolder, member: MemberData) {
        if (member.tag == 1) {
            view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.selected_view))
            vh.fotoFrameOverlay.setImageResource(R.drawable.circle_crop_selected)
        } else {
            if (winkerkEntry.RECORDSTATUS == "2") {
                view.setBackgroundColor(Color.parseColor("#ffd0d0"))
                vh.fotoFrameOverlay.setImageResource(R.drawable.circle_crop_onaktief)
            } else {
                vh.fotoFrameOverlay.setImageResource(R.drawable.circle_crop)
            }
        }
    }

    private fun bindBasicInfo(vh: ViewHolder, member: MemberData) {
        vh.nameTextView.text = member.name
        vh.vanTextView.text = member.surname
    }

    private fun bindContactInfo(vh: ViewHolder, member: MemberData) {
        if (member.cellphone.isNotEmpty() && member.cellphone.isNotBlank()) {
            val formattedCell = fixphonenumber(member.cellphone)
            if (winkerkEntry.LIST_SELFOON) {
                vh.selBlock.visibility = View.VISIBLE
                vh.cellTextView.text = formattedCell
            } else {
                vh.selBlock.visibility = View.GONE
            }
            if (MainActivity2.whatsappContacts.isNotEmpty() && winkerkEntry.LIST_WHATSAPP) {
                if (MainActivity2.whatsappContacts.contains(formattedCell)) {
                    vh.whatsappImageView.visibility = View.VISIBLE
                }
            }
        } else {
            vh.cellTextView.text = ""
        }

        if (member.ward.isNotEmpty() && winkerkEntry.LIST_WYK &&
            (winkerkEntry.SORTORDER == "VAN" || winkerkEntry.SORTORDER == "OUDERDOM" ||
                    winkerkEntry.SORTORDER == "VERJAAR" || winkerkEntry.SORTORDER == "HUWELIK")
        ) {
            vh.wykTextView.visibility = View.VISIBLE
            vh.wykTextView.text = member.ward
        } else {
            vh.wykTextView.text = ""
        }

        if (member.landline.isNotEmpty() && member.landline.isNotBlank()) {
            val formattedLandline = fixphonenumber(member.landline)
            if (winkerkEntry.LIST_TELEFOON) {
                vh.telBlock.visibility = View.VISIBLE
            } else {
                vh.telBlock.visibility = View.GONE
            }
            vh.telTextView.text = formattedLandline
        } else {
            vh.telTextView.text = ""
        }
    }

    private fun bindAgeInfo(vh: ViewHolder, member: MemberData) {
        if (member.birthday.isNotEmpty() && winkerkEntry.LIST_OUDERDOM && member.birthday.length >= 10) {
            vh.ouderdomTextView.text = "(${member.age})"
            vh.ouderdomTextView.visibility = View.VISIBLE
            val day = member.birthday.substring(0, 2)
            val month = member.birthday.substring(3, 5)
            vh.verjaarTextView.text = "$day ${getMonthAbbreviation(month)}"
            if (member.birthdayDT != null) {
                val today = DateTime.now()
                if (member.birthdayDT!!.monthOfYear == today.monthOfYear) {
                    if (winkerkEntry.LIST_VERJAARBLOK && member.birthdayDT!!.dayOfMonth == today.dayOfMonth) {
                        vh.koekImageView.visibility = View.VISIBLE
                    } else {
                        vh.koekImageView.visibility = View.GONE
                    }
                }
            }
        } else {
            vh.ouderdomTextView.text = ""
        }
    }

    private fun bindWeddingInfo(vh: ViewHolder, member: MemberData) {
        vh.huwelikTextView.visibility = View.GONE
        if (member.weddingDate.isNotEmpty() && winkerkEntry.LIST_HUWELIKBLOK && member.weddingDate.length > 6) {
            vh.ringImageView.visibility = View.VISIBLE
            val day = member.weddingDate.substring(0, 2)
            val month = member.weddingDate.substring(3, 5)
            vh.huwelikTextView.text = "$day ${getMonthAbbreviation(month)} (${member.weddingYears})"
            vh.huwelikTextView.visibility = View.VISIBLE
        }
    }

    private fun bindEmailIndicator(vh: ViewHolder, member: MemberData) {
        vh.eposImageView.visibility = View.GONE
        if (member.email.isNotEmpty() && member.email.isNotBlank() && winkerkEntry.LIST_EPOS) {
            vh.eposImageView.visibility = View.VISIBLE
        }
    }

    private fun handleSeparators(view: View, vh: ViewHolder, cursor: Cursor, member: MemberData) {
        var showSeparator = false
        var showSeparator2 = false
        var previousMaand = ""
        var maand = ""

        val position = cursor.position
        val rowStates = mRowStates

        if (rowStates != null && position < rowStates.size) {
            when (rowStates[position]) {
                SECTIONED_STATE -> {
                    showSeparator = true
                    showSeparator2 = true
                }
                SECTIONED_STATE2 -> {
                    showSeparator = false
                    showSeparator2 = true
                }
                REGULAR_STATE -> {
                    showSeparator = false
                    showSeparator2 = false
                }
                else -> {
                    if (position == 0) {
                        showSeparator = true
                        showSeparator2 = true
                    } else {
                        cursor.moveToPosition(position - 1)
                        val prevMember = extractMemberData(cursor)
                        cursor.moveToPosition(position)

                        when (winkerkEntry.SORTORDER) {
                            "WYK" -> {
                                if (prevMember.ward.isNotEmpty() && member.ward.isNotEmpty()) {
                                    if (prevMember.ward != member.ward) showSeparator = true
                                }
                                if (prevMember.familyHead != member.familyHead) showSeparator2 = true
                            }
                            "GESINNE" -> {
                                if (prevMember.familyHead != member.familyHead) showSeparator = true
                            }
                            "VAN" -> {
                                if (prevMember.surname.isNotEmpty() && member.surname.isNotEmpty()) {
                                    if (prevMember.surname[0] != member.surname[0]) showSeparator = true
                                }
                            }
                            "ADRES" -> {
                                if (prevMember.address != member.address) showSeparator = true
                            }
                            "VERJAAR" -> {
                                if (prevMember.birthday.isNotEmpty() && member.birthday.isNotEmpty()) {
                                    if (prevMember.birthday.length >= 5 && member.birthday.length >= 5) {
                                        previousMaand = prevMember.birthday.substring(3, 5)
                                        maand = member.birthday.substring(3, 5)
                                        if (previousMaand != maand) showSeparator = true
                                    }
                                }
                            }
                            "HUWELIK" -> {
                                if (prevMember.weddingDate.isNotEmpty() && member.weddingDate.isNotEmpty()) {
                                    if (prevMember.weddingDate.length >= 5 && member.weddingDate.length >= 5) {
                                        previousMaand = prevMember.weddingDate.substring(3, 5)
                                        maand = member.weddingDate.substring(3, 5)
                                        if (previousMaand != maand) showSeparator = true
                                    }
                                }
                            }
                            "OUDERDOM" -> {
                                if (prevMember.age != member.age) showSeparator = true
                            }
                        }
                    }

                    if (showSeparator && showSeparator2) rowStates[position] = SECTIONED_STATE
                    if (!showSeparator && showSeparator2) rowStates[position] = SECTIONED_STATE2
                    if (!showSeparator && !showSeparator2) rowStates[position] = REGULAR_STATE
                }
            }
        } else {
            if (position == 0) {
                showSeparator = true
                showSeparator2 = true
            } else {
                cursor.moveToPosition(position - 1)
                val prevMember = extractMemberData(cursor)
                cursor.moveToPosition(position)

                when (winkerkEntry.SORTORDER) {
                    "WYK" -> {
                        if (prevMember.ward.isNotEmpty() && member.ward.isNotEmpty()) {
                            if (prevMember.ward != member.ward) showSeparator = true
                        }
                        if (prevMember.familyHead != member.familyHead) showSeparator2 = true
                    }
                    "GESINNE" -> {
                        if (prevMember.familyHead != member.familyHead) showSeparator = true
                    }
                    "VAN" -> {
                        if (prevMember.surname.isNotEmpty() && member.surname.isNotEmpty()) {
                            if (prevMember.surname[0] != member.surname[0]) showSeparator = true
                        }
                    }
                    "ADRES" -> {
                        if (prevMember.address != member.address) showSeparator = true
                    }
                    "VERJAAR" -> {
                        if (prevMember.birthday.isNotEmpty() && member.birthday.isNotEmpty()) {
                            if (prevMember.birthday.length >= 5 && member.birthday.length >= 5) {
                                previousMaand = prevMember.birthday.substring(3, 5)
                                maand = member.birthday.substring(3, 5)
                                if (previousMaand != maand) showSeparator = true
                            }
                        }
                    }
                    "HUWELIK" -> {
                        if (prevMember.weddingDate.isNotEmpty() && member.weddingDate.isNotEmpty()) {
                            if (prevMember.weddingDate.length >= 5 && member.weddingDate.length >= 5) {
                                previousMaand = prevMember.weddingDate.substring(3, 5)
                                maand = member.weddingDate.substring(3, 5)
                                if (previousMaand != maand) showSeparator = true
                            }
                        }
                    }
                    "OUDERDOM" -> {
                        if (prevMember.age != member.age) showSeparator = true
                    }
                }
            }
        }

        if (showSeparator || showSeparator2) {
            configureSeparatorDisplay(vh, member, showSeparator, showSeparator2)
            vh.separatorBlock.visibility = View.VISIBLE
        } else {
            vh.separatorBlock.visibility = View.GONE
        }
    }

    private fun configureSeparatorDisplay(vh: ViewHolder, member: MemberData,
                                          showSeparator: Boolean, showSeparator2: Boolean) {
        when (winkerkEntry.SORTORDER) {
            "WYK" -> {
                var temp2 = member.address.replace("\r", "\n")
                temp2 = temp2.replace("\n\n", "\n")
                while (temp2.endsWith("\n")) {
                    temp2 = temp2.substring(0, temp2.length - 1)
                }
                val sp = if (showSeparator) {
                    SpannableString("${member.ward}\n$temp2").apply {
                        setSpan(RelativeSizeSpan(1.5f), 0, member.ward.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(RelativeSizeSpan(0.8f), member.ward.length + 1, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else if (showSeparator2) {
                    SpannableString(temp2).apply {
                        setSpan(RelativeSizeSpan(0.8f), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else {
                    SpannableString("")
                }
                vh.spearatorTextView.text = sp
                vh.spearatorWykTextView.text = "Wyk: ${member.ward}"
            }
            "VAN" -> {
                vh.spearatorTextView.text = if (member.surname.isNotEmpty()) member.surname.substring(0, 1) else ""
                vh.spearatorWykTextView.text = ""
            }
            "GESINNE" -> {
                var temp3 = member.address.replace("\r", "\n")
                temp3 = temp3.replace("\n\n", "\n")
                while (temp3.endsWith("\n")) {
                    temp3 = temp3.substring(0, temp3.length - 1)
                }
                val sp2 = SpannableString(temp3).apply {
                    setSpan(RelativeSizeSpan(0.8f), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                vh.spearatorTextView.text = sp2
                vh.spearatorWykTextView.text = "Wyk: ${member.ward}"
            }
            "ADRES" -> {
                var temp = member.address.replace("\r", "\n")
                temp = temp.replace("\n\n", "\n")
                while (temp.endsWith("\n")) {
                    temp = temp.substring(0, temp.length - 1)
                }
                vh.spearatorWykTextView.text = "Wyk: ${member.ward}"
                vh.spearatorTextView.text = temp
            }
            "VERJAAR", "HUWELIK" -> {
                vh.spearatorWykTextView.text = ""
                val month = when (winkerkEntry.SORTORDER) {
                    "VERJAAR" -> if (member.birthday.length >= 5) member.birthday.substring(3, 5) else ""
                    else -> if (member.weddingDate.length >= 5) member.weddingDate.substring(3, 5) else ""
                }
                vh.spearatorTextView.text = getMonthFullName(month)
            }
            "OUDERDOM" -> {
                vh.spearatorTextView.text = "${member.age} jaar"
                vh.spearatorWykTextView.text = ""
            }
        }
    }

    private fun getMonthAbbreviation(month: String): String {
        return when (month) {
            "01" -> "Jan"
            "02" -> "Feb"
            "03" -> "Mrt"
            "04" -> "Apr"
            "05" -> "Mei"
            "06" -> "Jun"
            "07" -> "Jul"
            "08" -> "Aug"
            "09" -> "Sept"
            "10" -> "Okt"
            "11" -> "Nov"
            "12" -> "Des"
            else -> ""
        }
    }

    private fun getMonthFullName(month: String): String {
        return when (month) {
            "01" -> "Januarie"
            "02" -> "Februarie"
            "03" -> "Maart"
            "04" -> "April"
            "05" -> "Mei"
            "06" -> "Junie"
            "07" -> "Julie"
            "08" -> "Augustus"
            "09" -> "September"
            "10" -> "Oktober"
            "11" -> "November"
            "12" -> "Desember"
            else -> ""
        }
    }

    fun highlight(search: String, originalText: String): CharSequence {
        if (search.isEmpty() || originalText.isEmpty()) return originalText
        val normalizedText = Normalizer.normalize(originalText, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase(Locale.ROOT)
        val searchLower = search.lowercase(Locale.ROOT)
        var start = normalizedText.indexOf(searchLower)
        if (start < 0) return originalText
        val highlighted = SpannableString(originalText)
        while (start >= 0) {
            val spanStart = start.coerceAtMost(originalText.length)
            val spanEnd = (start + search.length).coerceAtMost(originalText.length)
            highlighted.setSpan(BackgroundColorSpan(Color.YELLOW), spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = normalizedText.indexOf(searchLower, spanEnd)
        }
        return highlighted
    }

    class ViewHolder(view: View) {
        val nameTextView: TextView = view.findViewById(R.id.list_name)
        val vanTextView: TextView = view.findViewById(R.id.list_van)
        val cellTextView: TextView = view.findViewById(R.id.list_cellnumber)
        val telTextView: TextView = view.findViewById(R.id.list_landlyn)
        val wykTextView: TextView = view.findViewById(R.id.list_wyk)
        val ouderdomTextView: TextView = view.findViewById(R.id.list_ouderdom)
        val verjaarTextView: TextView = view.findViewById(R.id.list_verjaar)
        val huwelikTextView: TextView = view.findViewById(R.id.list_huwelik)
        val koekImageView: ImageView = view.findViewById(R.id.list_bday)
        val eposImageView: ImageView = view.findViewById(R.id.list_epos)
        val whatsappImageView: ImageView = view.findViewById(R.id.list_whatsapp)
        val fotoImageView: ImageView = view.findViewById(R.id.list_kontak_foto)
        val fotoFrameOverlay: ImageView = view.findViewById(R.id.circle_crop)
        val selBlock: LinearLayout = view.findViewById(R.id.list_cellBlock)
        val telBlock: LinearLayout = view.findViewById(R.id.list_telBlock)
        val fotoFrame: FrameLayout = view.findViewById(R.id.kontak_frame)
        val separatorBlock: LinearLayout = view.findViewById(R.id.list_seperatorBlok)
        val spearatorTextView: TextView = view.findViewById(R.id.list_separator)
        val spearatorWykTextView: TextView = view.findViewById(R.id.list_separatorwyk)
        val ringImageView: ImageView = view.findViewById(R.id.list_ring)
    }

    internal class LoadImage(
        private val imageViewRef: WeakReference<ImageView>,
        private val cache: LruCache<String, Bitmap>,
        private val path: String
    ) : Runnable {
        override fun run() {
            val bitmap = loadBitmap()
            if (bitmap != null) {
                Handler(Looper.getMainLooper()).post {
                    val imv = imageViewRef.get()
                    if (imv != null && path == imv.tag) {
                        imv.setImageBitmap(bitmap)
                        cache.put(path, bitmap)
                    }
                }
            }
        }

        private fun loadBitmap(): Bitmap? {
            val file = File(path)
            if (!file.exists()) return null
            return try {
                BitmapFactory.decodeFile(file.absolutePath)?.let { bitmap ->
                    ThumbnailUtils.extractThumbnail(bitmap, 48, 48)
                }
            } catch (e: Exception) {
                Log.e("LoadImage", "Error loading image: ${e.message}")
                null
            }
        }
    }

    private class MemberData {
        var name = ""
        var surname = ""
        var familyHead = ""
        var gender = ""
        var congregation = ""
        var cellphone = ""
        var landline = ""
        var email = ""
        var ward = ""
        var address = ""
        var birthday = ""
        var weddingDate = ""
        var picturePath = ""
        var tag = 0
        var age = "?"
        var weddingYears = "?"
        var birthdayDT: DateTime? = null
        var weddingDT: DateTime? = null
        var guid = ""
    }
}