package za.co.jpsoft.winkerkreader

import android.content.ActivityNotFoundException
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ContentUris
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import forceShowIcons
import org.joda.time.DateTime
import org.joda.time.Years
import za.co.jpsoft.winkerkreader.data.SpinnerAdapter
import za.co.jpsoft.winkerkreader.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.Utils.parseDate
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import java.io.File
import java.io.FileOutputStream

class lidmaat_detail_Activity : AppCompatActivity() {

    companion object {
        private const val TAG = "lidmaat_detail_Activity"
    }

    // UI Views
    private lateinit var mNameTextView: EditText
    private lateinit var mNooiensVanTextView: EditText
    private lateinit var mVanTextView: EditText
    private lateinit var mVolleNameTextView: EditText
    private lateinit var mSelfoonTextView: EditText
    private lateinit var mTelefoonTextView: EditText
    private lateinit var mWykTextView: EditText
    private lateinit var mGeboortedatumTextView: EditText
    private lateinit var mStraatadresTextView: EditText
    private lateinit var mPosadresTextView: EditText
    private lateinit var mLidmaatstatusTextView: EditText
    private lateinit var mGesinTextView: TextView
    private lateinit var mJAreOudTextView: TextView
    private lateinit var mEposTextView: EditText
    private lateinit var mBeroepTextView: EditText
    private lateinit var mWerkgewerTextView: EditText
    private lateinit var mBeroepBlock: LinearLayout
    private lateinit var mWerkgewerBlock: LinearLayout
    private lateinit var mSelfoonBlock: LinearLayout
    private lateinit var mTelefoonBlock: LinearLayout
    private lateinit var mEposBlock: LinearLayout
    private lateinit var mStraatadresBlock: LinearLayout
    private lateinit var mPosadresBlock: LinearLayout
    private lateinit var mNooiensvanBlock: LinearLayout
    private lateinit var mGesinTextViewBlock: LinearLayout
    private lateinit var mSelfooonIcon: ImageView
    private lateinit var mTelefoonIcon: ImageView
    private lateinit var mSmsIcon: ImageView
    private lateinit var mEposIcon: ImageView
    private lateinit var mWhatsappIcon: ImageView
    private lateinit var mKontakFoto: ImageView
    private lateinit var mWysigButton: Button
    private lateinit var mGeslagSpinner: Spinner
    private lateinit var mHuwelikstatusSpinner: Spinner

    private var mCursor: Cursor? = null
    private var mStraatAdres = ""
    private var mPosAdres = ""
    private var mLidmaatGUID: String? = null
    private var LIDMAAT_IN_USE = ""
    private var current_id = 0

    private val huwelikStatusArray = arrayOf("Getroud", "Ongetroud", "Geskei", "Weduwee", "Wewenaar", "Onbekend")
    private val geslagteArray = arrayOf("Vroulik", "Manlik")
    private val geslagPrente = intArrayOf(R.drawable.female, R.drawable.male)

    private var mGeslagB = ""
    private var mHuwelikstatus = "Ongetroud"
    private lateinit var mCurrentLidmaatUri: Uri
    private var mImageUri: Uri? = null
    private var mCurrentPicUri: String? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onImagePicked(it, false) }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && mImageUri != null) {
            onImagePicked(mImageUri!!, true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lidmaat_detail)

        winkerkEntry.id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val progressBar = findViewById<ProgressBar>(R.id.detail_indeterminateBar)
        val progressBar2 = findViewById<ProgressBar>(R.id.detail_indeterminateBar2)
        progressBar.visibility = View.GONE
        progressBar2.visibility = View.GONE

        val mylpaleBlock2 = findViewById<LinearLayout>(R.id.detail_mylpaleBlock2)
        mylpaleBlock2.visibility = View.GONE
        val groepeBlockm = findViewById<LinearLayout>(R.id.detail_groepBlockm)
        groepeBlockm.visibility = View.GONE
        val meelewingBlock = findViewById<LinearLayout>(R.id.detail_meelewingBlock)
        meelewingBlock.visibility = View.GONE
        val passieBlock = findViewById<LinearLayout>(R.id.detail_passieBlock)
        passieBlock.visibility = View.GONE
        val gawesBlock = findViewById<LinearLayout>(R.id.detail_gawesBlock)
        gawesBlock.visibility = View.GONE

        mCurrentLidmaatUri = intent.data ?: throw IllegalArgumentException("No data URI provided")

        val viewModel = ViewModelProvider(this)[LidmaatDetailViewModel::class.java]
        viewModel.loadMember(mCurrentLidmaatUri)

        viewModel.memberCursor.observe(this, Observer { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                displayMemberData(cursor)
                val familyHeadGuid = cursor.getStringOrNull(winkerkEntry.LIDMATE_GESINSHOOFGUID)
                if (familyHeadGuid != null) {
                    viewModel.loadFamily(familyHeadGuid)
                }
            }
        })

        viewModel.familyCursor.observe(this, Observer { cursor ->
            if (cursor != null && cursor.count > 1) {
                displayFamily(cursor)
            }
        })

        val gemeenten = findViewById<TextView>(R.id.detail_gemeentenaam)
        gemeenten.text = winkerkEntry.GEMEENTE_NAAM
        gemeenten.isSelected = true

        initializeViews()
        setupListeners()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private val cropImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val selectedImageUri = data?.data
            var path: String? = null
            if (selectedImageUri != null) {
                path = if (selectedImageUri.toString().startsWith("file")) {
                    selectedImageUri.toString().substring(7)
                } else {
                    getPathFromURI(selectedImageUri)
                }
                if (path == null) {
                    path = RealPathUtil.getRealPathFromURI_API19(this, selectedImageUri)
                }
            }
            if (!path.isNullOrEmpty()) {
                Log.i("Winkerkreader", "Image Path : $path")

                if (selectedImageUri?.toString()?.startsWith("file") == true) {
                    val bitmap = BitmapFactory.decodeFile(path)
                    mKontakFoto.setImageBitmap(bitmap)
                } else {
                    mKontakFoto.setImageURI(selectedImageUri)
                }

                val scale = resources.displayMetrics.density
                val pixels = (200 * scale + 0.5f).toInt()
                mKontakFoto.layoutParams.height = pixels
                mKontakFoto.layoutParams.width = pixels
                mKontakFoto.requestLayout()

                val newPath = copyFoto(path, winkerkEntry.LIDMAATGUID)

                val values = ContentValues().apply {
                    put(winkerkEntry.INFO_FOTO_PATH, newPath)
                    put(winkerkEntry.INFO_LIDMAAT_GUID, mLidmaatGUID)
                    put(winkerkEntry.INFO_GROUP, "")
                }

                if (mKontakFoto.tag != "default") {
                    contentResolver.update(winkerkEntry.INFO_LOADER_FOTO_URI, values, "${winkerkEntry.INFO_LIDMAAT_GUID} = ?", null)
                } else {
                    contentResolver.insert(winkerkEntry.INFO_LOADER_FOTO_URI, values)
                }

                val id = LIDMAAT_IN_USE.toIntOrNull() ?: 0
                val memberValues = ContentValues().apply {
                    put(winkerkEntry.LIDMATE_PICTUREPATH, newPath)
                }
                val memberUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, id.toLong())
                contentResolver.update(memberUri, memberValues, "${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ?", arrayOf(id.toString()))
            }
        }
    }

    private fun initializeViews() {
        mNameTextView = findViewById(R.id.detail_noemnaam)
        mVanTextView = findViewById(R.id.detail_van)
        mNooiensVanTextView = findViewById(R.id.detail_nooiensvan)
        mVolleNameTextView = findViewById(R.id.detail_vollename)
        mSelfoonTextView = findViewById(R.id.detail_selfoon)
        mTelefoonTextView = findViewById(R.id.detail_telefoon)
        mWykTextView = findViewById(R.id.detail_wyk)
        mGeboortedatumTextView = findViewById(R.id.detail_geboortedatum)
        mStraatadresTextView = findViewById(R.id.detail_straatadres)
        mPosadresTextView = findViewById(R.id.detail_posadres)
        mEposTextView = findViewById(R.id.detail_epos)
        mBeroepTextView = findViewById(R.id.detail_Beroep)
        mWerkgewerTextView = findViewById(R.id.detail_Werkgewer)
        mWysigButton = findViewById(R.id.buttonWysig)
        mGeslagSpinner = findViewById(R.id.geslag)
        mHuwelikstatusSpinner = findViewById(R.id.huwelikstatus)
        mJAreOudTextView = findViewById(R.id.detail_jareoud)
        mLidmaatstatusTextView = findViewById(R.id.detail_Lidmaatstatus)

        mSelfoonBlock = findViewById(R.id.detail_selfoonBlock)
        mTelefoonBlock = findViewById(R.id.detail_telefoonBlock)
        mEposBlock = findViewById(R.id.detail_eposBlock)
        mStraatadresBlock = findViewById(R.id.detail_straatadresBlock)
        mPosadresBlock = findViewById(R.id.detail_posadresBlock)
        mNooiensvanBlock = findViewById(R.id.detail_nooiensvanBlock)
        mGesinTextViewBlock = findViewById(R.id.detail_gesinBlock)
        mBeroepBlock = findViewById(R.id.detail_BeroepBlock)
        mWerkgewerBlock = findViewById(R.id.detail_WerkgewerBlock)
        mSelfooonIcon = findViewById(R.id.detail_selfoon_icon)
        mTelefoonIcon = findViewById(R.id.detail_landlyn_icon)
        mEposIcon = findViewById(R.id.detail_email_icon)
        mSmsIcon = findViewById(R.id.detail_sms_icon)
        mWhatsappIcon = findViewById(R.id.detail_whatsapp_icon)
        mKontakFoto = findViewById(R.id.detail_kontak_foto)

        listOf(
            mNameTextView, mVanTextView, mNooiensVanTextView, mVolleNameTextView,
            mSelfoonTextView, mTelefoonTextView, mWykTextView, mGeboortedatumTextView,
            mStraatadresTextView, mPosadresTextView, mEposTextView, mBeroepTextView,
            mWerkgewerTextView, mLidmaatstatusTextView
        ).forEach { it.isEnabled = false }
        mHuwelikstatusSpinner.isEnabled = false
        mGeslagSpinner.isEnabled = false

        mGesinTextViewBlock.visibility = View.GONE
    }

    private fun setupListeners() {
        mKontakFoto.setOnClickListener { showImagePopup() }

        mWysigButton.apply {
            isFocusable = true
            isClickable = true
            setOnClickListener { onWysigClick() }
        }

        mStraatadresBlock.setOnClickListener { openMapForAddress() }

        mSelfooonIcon.setOnClickListener { dialNumber(mSelfoonTextView.text.toString()) }
        mTelefoonIcon.setOnClickListener { dialNumber(mTelefoonTextView.text.toString()) }
        mWhatsappIcon.setOnClickListener { openWhatsApp() }
        mEposIcon.setOnClickListener { sendEmail() }
        mSmsIcon.setOnClickListener { openSms() }
    }

    private fun showImagePopup() {
        val popup = PopupMenu(this, mKontakFoto)
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

    private fun onWysigClick() {
        if (mWysigButton.text == getString(R.string.wysig)) {
            enableEditing(true)
            mWysigButton.text = getString(R.string.stoor)
            mWysigButton.setBackgroundColor(Color.RED)
            mStraatAdres = mStraatadresTextView.text.toString()
            mPosAdres = mPosadresTextView.text.toString()
            showSoftKeyboard(mWysigButton)
        } else {
            enableEditing(false)
            mWysigButton.text = getString(R.string.wysig)
            mWysigButton.setBackgroundColor(Color.parseColor("#0A064F"))
            mCursor?.let { wysigLidmaatData(it) }
            hideSoftKeyboard()
        }
    }

    private fun enableEditing(enable: Boolean) {
        listOf(
            mNameTextView, mVanTextView, mNooiensVanTextView, mVolleNameTextView,
            mSelfoonTextView, mTelefoonTextView, mWykTextView, mGeboortedatumTextView,
            mStraatadresTextView, mPosadresTextView, mEposTextView, mBeroepTextView,
            mWerkgewerTextView, mLidmaatstatusTextView
        ).forEach { it.isEnabled = enable }
        mHuwelikstatusSpinner.isEnabled = enable
        mGeslagSpinner.isEnabled = enable

        if (enable) {
            mSelfoonBlock.visibility = View.VISIBLE
            mTelefoonBlock.visibility = View.VISIBLE
            mStraatadresBlock.visibility = View.VISIBLE
            mPosadresBlock.visibility = View.VISIBLE
            mEposBlock.visibility = View.VISIBLE
            mNooiensvanBlock.visibility = View.VISIBLE
            mBeroepBlock.visibility = View.VISIBLE
            mWerkgewerBlock.visibility = View.VISIBLE
        }
    }

    private fun dialNumber(number: String) {
        if (number.isNotEmpty()) {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
            } catch (e: Exception) {
                Log.e(TAG, "Dial error", e)
            }
        }
    }

    private fun openWhatsApp() {
        val number = mSelfoonTextView.text.toString()
        if (number.isNotEmpty() && number.length >= 10) {
            val cell = fixphonenumber(number)?.replace("-", "")?.replace(" ", "") ?: ""
            if (cell.isNotEmpty()) {
                try {
                    val uri = Uri.parse("smsto: $cell")
                    val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                        `package` = "com.whatsapp"
                    }
                    startActivity(Intent.createChooser(intent, ""))
                } catch (_: Exception) {
                    Toast.makeText(this, "WhatsApp not Installed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendEmail() {
        val email = mEposTextView.text.toString()
        if (email.isNotEmpty()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:$email"))
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Email error", e)
            }
        }
    }

    private fun openSms() {
        val number = mSelfoonTextView.text.toString()
        if (number.isNotEmpty()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    type = "vnd.android-dir/mms-sms"
                    putExtra("address", number)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "SMS error", e)
            }
        }
    }

    private fun openMapForAddress() {
        val address = mStraatadresTextView.text.toString()
        if (address.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = "${mNameTextView.text} ${mVanTextView.text}\r\n$address"
            clipboard.setPrimaryClip(ClipData.newPlainText("text", clipData))
            Toast.makeText(this, clipData, Toast.LENGTH_SHORT).show()

            val encoded = address.replace("\n", "%20")
                .replace("\t", "%20")
                .replace("\r", "%2C")
                .replace(" ", "%20")
            val mapUri = "geo:0,0?q=$encoded"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapUri)))
        }
    }

    // ---------- display methods ----------

    private fun displayMemberData(cursor: Cursor) {
        mCursor = cursor
        current_id = cursor.getIntOrDefault("_id", 0)
        winkerkEntry.LIDMAATGUID = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATGUID)
        mLidmaatGUID = winkerkEntry.LIDMAATGUID

        // Load photo using the GUID
        loadMemberPhoto(winkerkEntry.LIDMAATGUID)

        val name = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
        val van = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)
        val voorname = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VOORNAME)
        val nooiens = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOOIENSVAN)
        val selfoon = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_SELFOON)
            .takeIf { it.isNotBlank() }?.let { fixphonenumber(it) } ?: ""
        val telefoon = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LANDLYN)
            .takeIf { it.isNotBlank() }?.let { fixphonenumber(it) } ?: ""
        val wyk = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_WYK)
        val bDayRaw = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)
        val straat = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_STRAATADRES)
            .replace("\r\n", ", ")
            .replace("\r", ", ")
            .replace("\n", ", ")
            .replace(", , ", ", ")
            .replace(",  ,", ", ")
        val pos = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_POSADRES)
            .replace("\r\n", ", ")
            .replace("\r", ", ")
            .replace("\n", ", ")
            .replace(", , ", ", ")
        val epos = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_EPOS)
        val beroep = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_BEROEP)
        val werkgewer = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_WERKGEWER)
        val geslag = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GESLAG)
        val huwelikStatus = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_HUWELIKSTATUS)
            .takeIf { it.isNotEmpty() } ?: "Ongetroud"
        val lidmaatStatus = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATSTATUS)
        val bewys = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_BEWYSSTATUS)

        var ageYears = 0
        var bDay = bDayRaw
        if (bDay.length >= 10) {
            bDay = bDay.substring(0, 10)
            try {
                val bDayDT = parseDate(bDay)
                ageYears = Years.yearsBetween(bDayDT, DateTime.now()).years
            } catch (_: Exception) { }
        }

        mNameTextView.setText(name)
        mVanTextView.setText(van)
        mVolleNameTextView.setText(voorname)
        mNooiensVanTextView.setText(nooiens)
        mSelfoonTextView.setText(selfoon)
        mTelefoonTextView.setText(telefoon)
        mWykTextView.setText(wyk)
        mGeboortedatumTextView.setText(bDay)
        mJAreOudTextView.text = if (ageYears < 0) "(?)" else "($ageYears)"
        mStraatadresTextView.setText(straat)
        mPosadresTextView.setText(pos)
        mEposTextView.setText(epos)
        mBeroepTextView.setText(beroep)
        mWerkgewerTextView.setText(werkgewer)
        mLidmaatstatusTextView.setText(lidmaatStatus)

        mLidmaatstatusTextView.setBackgroundColor(
            when (bewys) {
                "Ontvang" -> Color.WHITE
                "Aangevra" -> Color.GREEN
                "Nie Aangevra" -> Color.CYAN
                else -> Color.WHITE
            }
        )

        mSelfoonBlock.visibility = if (selfoon.isNotEmpty()) View.VISIBLE else View.GONE
        mTelefoonBlock.visibility = if (telefoon.isNotEmpty()) View.VISIBLE else View.GONE
        mEposBlock.visibility = if (epos.isNotEmpty()) View.VISIBLE else View.GONE
        mNooiensvanBlock.visibility = if (nooiens.isNotEmpty()) View.VISIBLE else View.GONE
        mBeroepBlock.visibility = if (beroep.isNotEmpty()) View.VISIBLE else View.GONE
        mWerkgewerBlock.visibility = if (werkgewer.isNotEmpty()) View.VISIBLE else View.GONE
        mStraatadresBlock.visibility = if (straat.isNotEmpty()) View.VISIBLE else View.GONE
        mPosadresBlock.visibility = if (pos.isNotEmpty()) View.VISIBLE else View.GONE

        val geslagAdapter = SpinnerAdapter(applicationContext, geslagPrente, null)
        mGeslagSpinner.adapter = geslagAdapter
        mGeslagSpinner.setSelection(if (geslag == "Manlik") 1 else 0)
        mGeslagB = geslag

        val huwelikAdapter = SpinnerAdapter(applicationContext, null, huwelikStatusArray)
        mHuwelikstatusSpinner.adapter = huwelikAdapter
        val huwPos = huwelikStatusArray.indexOfFirst { it == huwelikStatus }
        if (huwPos >= 0) mHuwelikstatusSpinner.setSelection(huwPos)
        mHuwelikstatus = huwelikStatus

        loadMilestones(cursor)
    }

    private fun loadMemberPhoto(guid: String) {
        val syncedPath = getSyncedPhotoPath(guid)
        Log.d(TAG, "loadMemberPhoto: guid = $guid, syncedPath = $syncedPath")

        if (syncedPath != null) {
            val file = File(syncedPath)
            if (file.exists()) {
                val scale = resources.displayMetrics.density
                val pixels = (200 * scale + 0.5f).toInt()
                mKontakFoto.layoutParams.height = pixels
                mKontakFoto.layoutParams.width = pixels
                mKontakFoto.requestLayout()

                val bitmap = BitmapFactory.decodeFile(syncedPath)
                if (bitmap != null) {
                    mKontakFoto.setImageBitmap(bitmap)
                    mKontakFoto.tag = "synced"
                    Log.d(TAG, "Photo loaded successfully")
                } else {
                    Log.e(TAG, "BitmapFactory.decodeFile returned null")
                    setDefaultPhoto()
                }
            } else {
                Log.e(TAG, "Photo file does not exist: $syncedPath")
                setDefaultPhoto()
            }
        } else {
            Log.d(TAG, "No synced photo found, using default")
            setDefaultPhoto()
        }
    }

    private fun displayFamily(cursor: Cursor) {
        mGesinTextViewBlock.visibility = View.VISIBLE
        mGesinTextViewBlock.removeAllViews()

        cursor.moveToPosition(-1)
        while (cursor.moveToNext()) {
            val memberId = cursor.getIntOrDefault("_id", -1)
            if (memberId == current_id) continue

            val naam = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_NOEMNAAM)
            val van = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)
            var bDay = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)
            val photo = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_PICTUREPATH)

            var age = 0
            if (bDay.isNotEmpty() && bDay.length >= 10) {
                bDay = bDay.substring(0, 10)
                try {
                    val bDayDT = parseDate(bDay)
                    age = Years.yearsBetween(bDayDT, DateTime.now()).years
                } catch (_: Exception) { }
            }
            val ageText = if (age < 0) "(?)" else "($age)"
            val gesinString = "\n$naam\t $van\t $bDay $ageText"

            val innerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val fotoFrame = FrameLayout(this)
            val imageViewOverlay = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(256, 256)
                scaleType = ImageView.ScaleType.FIT_XY
                setImageResource(R.drawable.circle_crop)
            }
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(256, 256)
                scaleType = ImageView.ScaleType.FIT_XY
                if (photo.isNotEmpty()) {
                    val file = File(winkerkEntry.CacheDir + photo)
                    if (file.exists()) {
                        setImageBitmap(BitmapFactory.decodeFile(winkerkEntry.CacheDir + photo))
                    } else {
                        setImageResource(R.drawable.clipboard)
                    }
                } else {
                    setImageResource(R.drawable.clipboard)
                }
            }
            fotoFrame.addView(imageView)
            fotoFrame.addView(imageViewOverlay)

            val textView = TextView(this).apply {
                text = gesinString
                setPadding(32, 0, 0, 0)
                TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
                tag = memberId
                setOnClickListener {
                    val gId = tag as Int
                    val intent = Intent(this@lidmaat_detail_Activity, lidmaat_detail_Activity::class.java)
                    winkerkEntry.LIDMAATID = gId
                    intent.data = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, gId.toLong())
                    startActivity(intent)
                    finish()
                }
            }

            innerLayout.addView(fotoFrame)
            innerLayout.addView(textView)
            mGesinTextViewBlock.addView(innerLayout)
        }
    }

    private fun loadMilestones(cursor: Cursor) {
        val mylpaleBlock = findViewById<LinearLayout>(R.id.detail_mylpaleBlock)
        val mylpaleBlock2 = findViewById<LinearLayout>(R.id.detail_mylpaleBlock2)

        mylpaleBlock.removeAllViews()
        mylpaleBlock2.visibility = View.GONE

        val doopDatum = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_DOOPDATUM)
        if (doopDatum.isNotEmpty()) {
            mylpaleBlock2.visibility = View.VISIBLE
            val doopText = "Doop\t\t($doopDatum)"
            val doopTv = TextView(this).apply {
                text = doopText
                TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
            }
            mylpaleBlock.addView(doopTv)
            val doopLeraar = cursor.getStringOrNull(winkerkEntry.LIDMATE_DOOPDS)
            if (!doopLeraar.isNullOrEmpty()) {
                val leraarTv = TextView(this).apply {
                    text = doopLeraar
                    TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Holo_Small)
                }
                mylpaleBlock.addView(leraarTv)
            }
        }

        val belyDatum = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_BELYDENISDATUM)
        if (belyDatum.isNotEmpty()) {
            mylpaleBlock2.visibility = View.VISIBLE
            val belyText = "Belydenis van geloof\t\t($belyDatum)"
            val belyTv = TextView(this).apply {
                text = belyText
                TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
            }
            mylpaleBlock.addView(belyTv)
            val belyLeraar = cursor.getStringOrNull(winkerkEntry.LIDMATE_BELYDENISDS)
            if (!belyLeraar.isNullOrEmpty()) {
                val leraarTv = TextView(this).apply {
                    text = belyLeraar
                    TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Holo_Small)
                }
                mylpaleBlock.addView(leraarTv)
            }
        }

        val huwelikDatum = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_HUWELIKSDATUM)
        if (huwelikDatum.isNotEmpty()) {
            mylpaleBlock2.visibility = View.VISIBLE
            var huwelikText = "Huwelik\t\t($huwelikDatum)"
            if (huwelikDatum.isNotEmpty()) {
                try {
                    val dt = parseDate(huwelikDatum)
                    val years = Years.yearsBetween(dt, DateTime.now()).years
                    huwelikText = "$huwelikText : $years jaar)"
                } catch (_: Exception) { }
            }
            val huwelikTv = TextView(this).apply {
                text = huwelikText
                TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
            }
            mylpaleBlock.addView(huwelikTv)
        }
    }

    private fun getSyncedPhotoPath(guid: String?): String? {
        if (guid.isNullOrEmpty()) return null
        val externalDir = getExternalFilesDir(null) ?: return null
        val syncedFile = File(externalDir, "photos/$guid.jpg")
        return if (syncedFile.exists()) syncedFile.absolutePath else null
    }

    private fun setDefaultPhoto() {
        val scale = resources.displayMetrics.density
        val pixels = (50 * scale + 0.5f).toInt()
        mKontakFoto.layoutParams.height = pixels
        mKontakFoto.layoutParams.width = pixels
        mKontakFoto.requestLayout()
        mKontakFoto.setImageResource(R.drawable.kontaks)
        mKontakFoto.tag = "default"
    }

    private fun wysigLidmaatData(cursor: Cursor) {
        val id = cursor.getIntOrDefault("_id", -1)
        winkerkEntry.LIDMAATGUID = cursor.getStringOrEmpty(winkerkEntry.LIDMATE_LIDMAATGUID)

        val values = ContentValues()
        var emailText = ""
        var emailHtml = "<html>"

        val subject = "Opdateer asb Winkerkdata van Lidmaat: ${cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VOORNAME)} ${cursor.getStringOrEmpty(winkerkEntry.LIDMATE_VAN)}"
        emailHtml += "<p>Wyk: ${cursor.getStringOrEmpty(winkerkEntry.LIDMATE_WYK)}<br>Geboortedatum: ${cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)}</p>"
        emailText = "$subject\r\nWyk: ${cursor.getStringOrEmpty(winkerkEntry.LIDMATE_WYK)}\r\nGeboortedatum: ${cursor.getStringOrEmpty(winkerkEntry.LIDMATE_GEBOORTEDATUM)}"

        fun checkAndPut(column: String, currentValue: String?, originalValue: String?) {
            if (currentValue != originalValue) {
                values.put(column, currentValue)
                emailHtml += "\r\n<p>$column : <b><font color='red'>$currentValue</font></b></p>"
                emailText += "\r\n$column : $currentValue"
            }
        }

        checkAndPut(winkerkEntry.LIDMATE_NOEMNAAM, mNameTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_NOEMNAAM))
        checkAndPut(winkerkEntry.LIDMATE_VAN, mVanTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_VAN))
        checkAndPut(winkerkEntry.LIDMATE_VOORNAME, mVolleNameTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_VOORNAME))
        checkAndPut(winkerkEntry.LIDMATE_SELFOON, mSelfoonTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_SELFOON))
        checkAndPut(winkerkEntry.LIDMATE_LANDLYN, mTelefoonTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_LANDLYN))
        checkAndPut(winkerkEntry.LIDMATE_WYK, mWykTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_WYK))
        checkAndPut(winkerkEntry.LIDMATE_LIDMAATSTATUS, mLidmaatstatusTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_LIDMAATSTATUS))
        checkAndPut(winkerkEntry.LIDMATE_GEBOORTEDATUM, mGeboortedatumTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_GEBOORTEDATUM))
        checkAndPut(winkerkEntry.LIDMATE_EPOS, mEposTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_EPOS))
        checkAndPut(winkerkEntry.LIDMATE_NOOIENSVAN, mNooiensVanTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_NOOIENSVAN))
        checkAndPut(winkerkEntry.LIDMATE_BEROEP, mBeroepTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_BEROEP))
        checkAndPut(winkerkEntry.LIDMATE_WERKGEWER, mWerkgewerTextView.text.toString(), cursor.getStringOrNull(winkerkEntry.LIDMATE_WERKGEWER))

        val newStraat = mStraatadresTextView.text.toString()
        val oldStraat = cursor.getStringOrNull(winkerkEntry.LIDMATE_STRAATADRES)
        if (newStraat != mStraatAdres) {
            values.put(winkerkEntry.LIDMATE_STRAATADRES, newStraat)
            emailHtml += "\r\n<p>${winkerkEntry.LIDMATE_STRAATADRES} : <b><font color='red'>$newStraat</font></b></p>"
            emailText += "\r\n${winkerkEntry.LIDMATE_STRAATADRES} : $newStraat"
        }

        val newPos = mPosadresTextView.text.toString()
        val oldPos = cursor.getStringOrNull(winkerkEntry.LIDMATE_POSADRES)
        if (newPos != mPosAdres) {
            values.put(winkerkEntry.LIDMATE_POSADRES, newPos)
            emailHtml += "\r\n<p>${winkerkEntry.LIDMATE_POSADRES} : <b><font color='red'>$newPos</font></b></p>"
            emailText += "\r\n${winkerkEntry.LIDMATE_POSADRES} : $newPos"
        }

        val huwPos = mHuwelikstatusSpinner.selectedItemPosition
        val newHuwelik = huwelikStatusArray[huwPos]
        val oldHuwelik = cursor.getStringOrNull(winkerkEntry.LIDMATE_HUWELIKSTATUS)
        if (newHuwelik != oldHuwelik) {
            values.put(winkerkEntry.LIDMATE_HUWELIKSTATUS, newHuwelik)
            emailHtml += "\r\n<p>${winkerkEntry.LIDMATE_HUWELIKSTATUS} : <b><font color='red'>$newHuwelik</font></b></p>"
            emailText += "\r\n${winkerkEntry.LIDMATE_HUWELIKSTATUS} : $newHuwelik"
        }

        val geslagPos = mGeslagSpinner.selectedItemPosition
        val newGeslag = geslagteArray[geslagPos]
        if (newGeslag != mGeslagB) {
            values.put(winkerkEntry.LIDMATE_GESLAG, newGeslag)
            emailHtml += "\r\n<p>${winkerkEntry.LIDMATE_GESLAG} : <b><font color='red'>$newGeslag</font></b></p>"
            emailText += "\r\n${winkerkEntry.LIDMATE_GESLAG} : $newGeslag"
        }

        emailHtml += "</html>"

        if (values.size() > 0) {
            val uri = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, id.toLong())
            contentResolver.update(uri, values, "${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ?", arrayOf(id.toString()))
        }

        var emailUrl = ""
        val gemeente = cursor.getStringOrNull(winkerkEntry.LIDMATE_GEMEENTE)
        emailUrl = when (gemeente) {
            winkerkEntry.GEMEENTE_NAAM -> winkerkEntry.GEMEENTE_EPOS
            winkerkEntry.GEMEENTE2_NAAM -> winkerkEntry.GEMEENTE2_EPOS
            winkerkEntry.GEMEENTE3_NAAM -> winkerkEntry.GEMEENTE3_EPOS
            else -> ""
        }

        val prefs = getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
        val eposHtmlEnabled = prefs.getBoolean("EposHtml", false)

        val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailUrl))
            if (eposHtmlEnabled) {
                @Suppress("DEPRECATION")
                val html: Spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(emailHtml, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    Html.fromHtml(emailHtml)
                }
                putExtra(Intent.EXTRA_TEXT, html)
            } else {
                putExtra(Intent.EXTRA_TEXT, emailText)
            }
        }

        try {
            startActivity(sendIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Email intent failed", e)
        }
    }

    private fun hideSoftKeyboard() {
        currentFocus?.let {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun showSoftKeyboard(view: View) {
        view.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, 0)
    }

    private fun openImageChooser() {
        pickImageLauncher.launch("image/*")
    }

    private fun onImagePicked(uri: Uri, isCamera: Boolean) {
        var path = if (isCamera) {
            uri.path ?: ""
        } else {
            getPathFromURI(uri) ?: ""
        }
        if (path.isEmpty()) {
            path = RealPathUtil.getRealPathFromURI_API19(this, uri) ?: ""
        }
        if (path.isNotEmpty()) {
            startCrop(uri)
        }
    }

    private fun startCrop(uri: Uri) {
        val cropIntent = Intent("com.android.camera.action.CROP").apply {
            setDataAndType(uri, "image/*")
            putExtra("crop", "true")
            putExtra("aspectX", 1)
            putExtra("aspectY", 1)
            putExtra("return-data", true)
        }
        try {
            cropImageLauncher.launch(cropIntent)
        } catch (_: ActivityNotFoundException) {
            // ignore
        }
    }

    private fun getPathFromURI(contentUri: Uri): String? {
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        return contentResolver.query(contentUri, proj, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                cursor.getString(columnIndex)
            } else null
        }
    }

    private fun copyFoto(path: String, guid: String): String {
        if (path.isEmpty()) return ""

        val width = winkerkEntry.THUMBSIZE
        val height = winkerkEntry.THUMBSIZE

        val cacheDir = File(winkerkEntry.CacheDir)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val thumbFile = File(cacheDir, "$guid.png")
        if (thumbFile.exists()) thumbFile.delete()
        try {
            val bitmap = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(path), width, height)
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail save error", e)
        }

        val externalDir = getExternalFilesDir(null)
        if (externalDir != null) {
            val photoDir = File(externalDir, "photos")
            if (!photoDir.exists()) photoDir.mkdirs()
            val photoFile = File(photoDir, "$guid.jpg")
            if (photoFile.exists()) photoFile.delete()
            try {
                val bitmap = BitmapFactory.decodeFile(path)
                FileOutputStream(photoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Full photo save error", e)
            }
        }

        return "$guid.jpg"
    }

    private fun setForceShowIcon(popupMenu: PopupMenu) {
        try {
            val fields = popupMenu.javaClass.declaredFields
            for (field in fields) {
                if ("mPopup" == field.name) {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popupMenu)
                    val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                    val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
                    setForceIcons.invoke(menuPopupHelper, true)
                    break
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "setForceShowIcon error", e)
        }
    }

    private fun kamera() {
        try {
            val photo = createTemporaryFile("picture", ".jpg")
            photo.delete()
            val imageUri = Uri.fromFile(photo)
            mImageUri = imageUri
            mCurrentPicUri = imageUri.path
            takePictureLauncher.launch(imageUri)
        } catch (e: Exception) {
            Toast.makeText(this, "Please check SD card! Image shot is impossible!", Toast.LENGTH_LONG).show()
        }
    }

    @Throws(Exception::class)
    private fun createTemporaryFile(part: String, ext: String): File {
        val tempDir = File("${winkerkEntry.FotoDir}/.temp/")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return File.createTempFile(part, ext, tempDir)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return false
    }
}