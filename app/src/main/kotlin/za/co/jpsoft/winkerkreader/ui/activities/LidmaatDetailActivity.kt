package za.co.jpsoft.winkerkreader.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
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
import android.provider.Settings
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.ui.adapters.SpinnerAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailViewModel
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.DeviceIdManager
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import za.co.jpsoft.winkerkreader.utils.Utils.parseDate
import za.co.jpsoft.winkerkreader.utils.forceShowIcons
import za.co.jpsoft.winkerkreader.utils.getIntOrDefault
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty
import za.co.jpsoft.winkerkreader.utils.getStringOrNull
import za.co.jpsoft.winkerkreader.data.models.MemberDetailItem
import za.co.jpsoft.winkerkreader.data.models.FamilyMemberItem

class LidmaatDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LidmaatDetailActivity"
        private const val STATE_IMAGE_URI = "image_uri"
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
    private lateinit var settingsManager: SettingsManager

    private var current_id = 0
    private var mLidmaatGUID: String? = null
    private var mStraatAdres: String = ""
    private var mPosAdres: String = ""
    private var recordStatus: String = "0"
    private var mCursor: Cursor? = null // Keeping for compatibility, though we'll prefer MemberDetailItem
    private lateinit var viewModel: LidmaatDetailViewModel

    private val huwelikStatusArray =
            arrayOf("Getroud", "Ongetroud", "Geskei", "Weduwee", "Wewenaar", "Onbekend")
    private val geslagteArray = arrayOf("Vroulik", "Manlik")
    private val geslagPrente = intArrayOf(R.drawable.female, R.drawable.male)

    private var mGeslagB = ""
    private var mHuwelikstatus = "Ongetroud"
    private lateinit var mCurrentLidmaatUri: Uri
    private var mImageUri: Uri? = null
    private var mCurrentPicUri: String? = null

    // Photo Picker for gallery selection (Android 4.4+)
    private val photoPickerLauncher =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                uri?.let { processSelectedImage(it, isCamera = false) }
            }

    // Camera launcher (unchanged)
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = mImageUri
            if (success && uri != null) {
                processSelectedImage(uri, isCamera = true)
            } else {
                if (uri == null) {
                    Log.e(TAG, "Camera returned but image URI is null (activity state lost)")
                    Toast.makeText(this, "Camera error: lost image URI", Toast.LENGTH_SHORT).show()
                }
            }
            // Clear the temporary URI to avoid re-use
            mImageUri = null
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mImageUri?.let { outState.putString(STATE_IMAGE_URI, it.toString()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager.getInstance(this)
        setContentView(R.layout.lidmaat_detail)

        // Removed AppSessionState.deviceId assignment
        val deviceId = DeviceIdManager.getDeviceId(this)

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
        recordStatus = intent.getStringExtra("RECORD_STATUS") ?: "0"

        viewModel = ViewModelProvider(this)[LidmaatDetailViewModel::class.java]
        viewModel.loadMember(mCurrentLidmaatUri, recordStatus)

        viewModel.memberDetail.observe(this) { item ->
            if (item != null) {
                displayMemberData(item)
                if (item.familyHeadGuid.isNotEmpty()) {
                    viewModel.loadFamily(item.familyHeadGuid, recordStatus)
                }
            }
        }

        viewModel.familyMembers.observe(this) { members ->
            if (members.isNotEmpty()) {
                displayFamily(members)
            }
        }

        val gemeenten = findViewById<TextView>(R.id.detail_gemeentenaam)
        gemeenten.text = settingsManager.gemeenteNaam
        gemeenten.isSelected = true

        initializeViews()
        setupListeners()

        onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        finish()
                    }
                }
        )

        // Restore pending image URI if any
        savedInstanceState?.getString(STATE_IMAGE_URI)?.let { uriString ->
            mImageUri = Uri.parse(uriString)
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
                        mNameTextView,
                        mVanTextView,
                        mNooiensVanTextView,
                        mVolleNameTextView,
                        mSelfoonTextView,
                        mTelefoonTextView,
                        mWykTextView,
                        mGeboortedatumTextView,
                        mStraatadresTextView,
                        mPosadresTextView,
                        mEposTextView,
                        mBeroepTextView,
                        mWerkgewerTextView,
                        mLidmaatstatusTextView
                )
                .forEach { it.isEnabled = false }
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
            viewModel.memberDetail.value?.let { wysigLidmaatData(it) }
            hideSoftKeyboard()
        }
    }

    private fun enableEditing(enable: Boolean) {
        listOf(
                        mNameTextView,
                        mVanTextView,
                        mNooiensVanTextView,
                        mVolleNameTextView,
                        mSelfoonTextView,
                        mTelefoonTextView,
                        mWykTextView,
                        mGeboortedatumTextView,
                        mStraatadresTextView,
                        mPosadresTextView,
                        mEposTextView,
                        mBeroepTextView,
                        mWerkgewerTextView,
                        mLidmaatstatusTextView
                )
                .forEach { it.isEnabled = enable }
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
                    val intent =
                            Intent(Intent.ACTION_SENDTO, uri).apply { `package` = "com.whatsapp" }
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
                val intent =
                        Intent(Intent.ACTION_VIEW).apply {
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

            val encoded =
                    address.replace("\n", "%20")
                            .replace("\t", "%20")
                            .replace("\r", "%2C")
                            .replace(" ", "%20")
            val mapUri = "geo:0,0?q=$encoded"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapUri)))
        }
    }

    // ---------- display methods ----------

    private fun displayMemberData(item: MemberDetailItem) {
        current_id = item.id
        mLidmaatGUID = item.guid

        // Load photo using the GUID
        loadMemberPhoto(item.guid)

        mNameTextView.setText(item.name)
        mVanTextView.setText(item.surname)
        mVolleNameTextView.setText(item.fullNames)
        mNooiensVanTextView.setText(item.maidenName)
        mSelfoonTextView.setText(item.cellphone)
        mTelefoonTextView.setText(item.landline)
        mWykTextView.setText(item.ward)
        mGeboortedatumTextView.setText(item.birthday)
        mJAreOudTextView.text = if (item.age < 0) "(?)" else "(${item.age})"
        mStraatadresTextView.setText(item.streetAddress)
        mPosadresTextView.setText(item.postalAddress)
        mEposTextView.setText(item.email)
        mBeroepTextView.setText(item.profession)
        mWerkgewerTextView.setText(item.employer)
        mLidmaatstatusTextView.setText(item.memberStatus)

        mLidmaatstatusTextView.setBackgroundColor(
                when (item.certificateStatus) {
                    "Ontvang" -> Color.WHITE
                    "Aangevra" -> Color.GREEN
                    "Nie Aangevra" -> Color.CYAN
                    else -> Color.WHITE
                }
        )

        mSelfoonBlock.visibility = if (item.cellphone.isNotEmpty()) View.VISIBLE else View.GONE
        mTelefoonBlock.visibility = if (item.landline.isNotEmpty()) View.VISIBLE else View.GONE
        mEposBlock.visibility = if (item.email.isNotEmpty()) View.VISIBLE else View.GONE
        mNooiensvanBlock.visibility = if (item.maidenName.isNotEmpty()) View.VISIBLE else View.GONE
        mBeroepBlock.visibility = if (item.profession.isNotEmpty()) View.VISIBLE else View.GONE
        mWerkgewerBlock.visibility = if (item.employer.isNotEmpty()) View.VISIBLE else View.GONE
        mStraatadresBlock.visibility = if (item.streetAddress.isNotEmpty()) View.VISIBLE else View.GONE
        mPosadresBlock.visibility = if (item.postalAddress.isNotEmpty()) View.VISIBLE else View.GONE

        val geslagAdapter = SpinnerAdapter(applicationContext, geslagPrente, null)
        mGeslagSpinner.adapter = geslagAdapter
        mGeslagSpinner.setSelection(if (item.gender == "Manlik") 1 else 0)
        mGeslagB = item.gender

        val huwelikAdapter = SpinnerAdapter(applicationContext, null, huwelikStatusArray)
        mHuwelikstatusSpinner.adapter = huwelikAdapter
        val huwPos = huwelikStatusArray.indexOfFirst { it == item.marriageStatus }
        if (huwPos >= 0) mHuwelikstatusSpinner.setSelection(huwPos)
        mHuwelikstatus = item.marriageStatus

        loadMilestones(item)
    }

    private fun loadMemberPhoto(guid: String?) {
        if (guid == null) {
            setDefaultPhoto()
            return
        }
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

    private fun displayFamily(members: List<FamilyMemberItem>) {
        mGesinTextViewBlock.visibility = View.VISIBLE
        mGesinTextViewBlock.removeAllViews()

        for (member in members) {
            if (member.id == current_id) continue

            val ageText = if (member.age < 0) "(?)" else "(${member.age})"
            val gesinString = "\n${member.name}\t ${member.surname}\t ${member.birthday} $ageText"

            val innerLayout =
                    LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

            val fotoFrame = FrameLayout(this)
            val imageViewOverlay =
                    ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(256, 256)
                        scaleType = ImageView.ScaleType.FIT_XY
                        setImageResource(R.drawable.circle_crop)
                    }
            val imageView =
                    ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(256, 256)
                        scaleType = ImageView.ScaleType.FIT_XY
                        if (member.picturePath.isNotEmpty()) {
                            val file =
                                    File(
                                            winkerkEntry.getCacheDir(this@LidmaatDetailActivity) +
                                                    member.picturePath
                                    )
                            if (file.exists()) {
                                setImageBitmap(
                                        BitmapFactory.decodeFile(
                                                winkerkEntry.getCacheDir(
                                                        this@LidmaatDetailActivity
                                                ) + member.picturePath
                                        )
                                )
                            } else {
                                setImageResource(R.drawable.clipboard)
                            }
                        } else {
                            setImageResource(R.drawable.clipboard)
                        }
                    }
            fotoFrame.addView(imageView)
            fotoFrame.addView(imageViewOverlay)

            val textView =
                    TextView(this).apply {
                        text = gesinString
                        setPadding(32, 0, 0, 0)
                        TextViewCompat.setTextAppearance(
                                this,
                                android.R.style.TextAppearance_Medium
                        )
                        tag = member.id
                        setOnClickListener {
                            val gId = tag as Int
                            val intent =
                                    Intent(
                                            this@LidmaatDetailActivity,
                                            LidmaatDetailActivity::class.java
                                    ).apply {
                                        data = ContentUris.withAppendedId(
                                                winkerkEntry.CONTENT_URI,
                                                gId.toLong()
                                        )
                                        putExtra("RECORD_STATUS", recordStatus)
                                    }
                            startActivity(intent)
                            finish()
                        }
                    }

            innerLayout.addView(fotoFrame)
                            innerLayout.addView(textView)
            mGesinTextViewBlock.addView(innerLayout)
        }
    }

    private fun loadMilestones(item: MemberDetailItem) {
        val mylpaleBlock = findViewById<LinearLayout>(R.id.detail_mylpaleBlock)
        val mylpaleBlock2 = findViewById<LinearLayout>(R.id.detail_mylpaleBlock2)
        mylpaleBlock.removeAllViews()
        mylpaleBlock2.visibility = View.GONE

        if (item.baptismDate.isNotEmpty()) {
            mylpaleBlock2.visibility = View.VISIBLE
            val doopText = "Doop\t\t\t\t\t(${item.baptismDate})"
            val doopTv =
                    TextView(this).apply {
                        text = doopText
                        TextViewCompat.setTextAppearance(
                                this,
                                android.R.style.TextAppearance_Medium
                        )
                    }
            mylpaleBlock.addView(doopTv)
            if (item.baptismDs.isNotEmpty()) {
                val leraarTv =
                        TextView(this).apply {
                            text = item.baptismDs
                            TextViewCompat.setTextAppearance(
                                    this,
                                    android.R.style.TextAppearance_Holo_Small
                            )
                        }
                mylpaleBlock.addView(leraarTv)
            }
        }

        if (item.confessionDate.isNotEmpty()) {
            mylpaleBlock2.visibility = View.VISIBLE
            val belyText = "Belydenis van geloof\t\t(${item.confessionDate})"
            val belyTv =
                    TextView(this).apply {
                        text = belyText
                        TextViewCompat.setTextAppearance(
                                this,
                                android.R.style.TextAppearance_Medium
                        )
                    }
            mylpaleBlock.addView(belyTv)
            if (item.confessionDs.isNotEmpty()) {
                val leraarTv =
                        TextView(this).apply {
                            text = item.confessionDs
                            TextViewCompat.setTextAppearance(
                                    this,
                                    android.R.style.TextAppearance_Holo_Small
                            )
                        }
                mylpaleBlock.addView(leraarTv)
            }
        }

        if (item.marriageDate.isNotEmpty()) {
            mylpaleBlock2.visibility = View.VISIBLE
            var huwelikText = "Huwelik\t\t(${item.marriageDate})"
            if (item.marriageYears >= 0) {
                huwelikText = "$huwelikText : ${item.marriageYears} jaar)"
            }
            val huwelikTv =
                    TextView(this).apply {
                        text = huwelikText
                        TextViewCompat.setTextAppearance(
                                this,
                                android.R.style.TextAppearance_Medium
                        )
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

    private fun wysigLidmaatData(item: MemberDetailItem) {
        val id = item.id
        mLidmaatGUID = item.guid

        val values = ContentValues()
        var emailText = ""
        var emailHtml = "<html>"

        val subject = "Opdateer asb Winkerkdata van Lidmaat: ${item.fullNames} ${item.surname}"
        emailHtml += "<p>Wyk: ${item.ward}<br>Geboortedatum: ${item.birthday}</p>"
        emailText = "$subject\r\nWyk: ${item.ward}\r\nGeboortedatum: ${item.birthday}"

        fun checkAndPut(column: String, currentValue: String, originalValue: String) {
            if (currentValue != originalValue) {
                values.put(column, currentValue)
                emailHtml += "\r\n<p>$column : <b><font color='red'>$currentValue</font></b></p>"
                emailText += "\r\n$column : $currentValue"
            }
        }

        checkAndPut(winkerkEntry.LIDMATE_NOEMNAAM, mNameTextView.text.toString(), item.name)
        checkAndPut(winkerkEntry.LIDMATE_VAN, mVanTextView.text.toString(), item.surname)
        checkAndPut(winkerkEntry.LIDMATE_VOORNAME, mVolleNameTextView.text.toString(), item.fullNames)
        checkAndPut(winkerkEntry.LIDMATE_SELFOON, mSelfoonTextView.text.toString(), item.cellphone)
        checkAndPut(winkerkEntry.LIDMATE_LANDLYN, mTelefoonTextView.text.toString(), item.landline)
        checkAndPut(winkerkEntry.LIDMATE_WYK, mWykTextView.text.toString(), item.ward)
        checkAndPut(winkerkEntry.LIDMATE_LIDMAATSTATUS, mLidmaatstatusTextView.text.toString(), item.memberStatus)
        checkAndPut(winkerkEntry.LIDMATE_GEBOORTEDATUM, mGeboortedatumTextView.text.toString(), item.birthday)
        checkAndPut(winkerkEntry.LIDMATE_EPOS, mEposTextView.text.toString(), item.email)
        checkAndPut(winkerkEntry.LIDMATE_NOOIENSVAN, mNooiensVanTextView.text.toString(), item.maidenName)
        checkAndPut(winkerkEntry.LIDMATE_BEROEP, mBeroepTextView.text.toString(), item.profession)
        checkAndPut(winkerkEntry.LIDMATE_WERKGEWER, mWerkgewerTextView.text.toString(), item.employer)

        val newStraat = mStraatadresTextView.text.toString()
        if (newStraat != mStraatAdres) {
            values.put(winkerkEntry.LIDMATE_STRAATADRES, newStraat)
            emailHtml += "\r\n<p>${winkerkEntry.LIDMATE_STRAATADRES} : <b><font color='red'>$newStraat</font></b></p>"
            emailText += "\r\n${winkerkEntry.LIDMATE_STRAATADRES} : $newStraat"
        }

        val newPos = mPosadresTextView.text.toString()
        if (newPos != mPosAdres) {
            values.put(winkerkEntry.LIDMATE_POSADRES, newPos)
            emailHtml += "\r\n<p>${winkerkEntry.LIDMATE_POSADRES} : <b><font color='red'>$newPos</font></b></p>"
            emailText += "\r\n${winkerkEntry.LIDMATE_POSADRES} : $newPos"
        }

        val huwPos = mHuwelikstatusSpinner.selectedItemPosition
        val newHuwelik = huwelikStatusArray[huwPos]
        if (newHuwelik != item.marriageStatus) {
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
            contentResolver.update(
                    uri,
                    values,
                    "${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ?",
                    arrayOf(id.toString())
            )
        }

        var emailUrl = ""
        val gemeente = item.gemeente
        emailUrl =
                when (gemeente) {
                    settingsManager.gemeenteNaam -> settingsManager.gemeenteEpos
                    settingsManager.gemeente2Naam -> settingsManager.gemeente2Epos
                    settingsManager.gemeente3Naam -> settingsManager.gemeente3Epos
                    else -> ""
                }

        val prefs = getSharedPreferences(PREFS_USER_INFO, Context.MODE_PRIVATE)
        val eposHtmlEnabled = prefs.getBoolean("EposHtml", false)

        val sendIntent =
                Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(emailUrl))
                    if (eposHtmlEnabled) {
                        @Suppress("DEPRECATION")
                        val html: Spanned =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
        // Launch Photo Picker – no need to request storage permissions
        photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun processSelectedImage(imageUri: Uri, isCamera: Boolean) {
        // Directly call the updated copyFoto with the URI
        val newPath = copyFoto(imageUri, mLidmaatGUID)
        if (newPath.isEmpty()) {
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
            return
        }

        // Update UI
        val scale = resources.displayMetrics.density
        val pixels = (200 * scale + 0.5f).toInt()
        mKontakFoto.layoutParams.height = pixels
        mKontakFoto.layoutParams.width = pixels
        mKontakFoto.requestLayout()

        // Load bitmap from the saved full-size file (or directly from URI)
        val bitmap =
                try {
                    contentResolver.openInputStream(imageUri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: Exception) {
                    null
                }
        if (bitmap != null) {
            mKontakFoto.setImageBitmap(bitmap)
            mKontakFoto.tag = "synced"
        } else {
            setDefaultPhoto()
        }

        // Save reference in database
        val values =
                ContentValues().apply {
                    put(winkerkEntry.INFO_FOTO_PATH, newPath)
                    put(winkerkEntry.INFO_LIDMAAT_GUID, mLidmaatGUID)
                    put(winkerkEntry.INFO_GROUP, "")
                }

        if (mKontakFoto.tag != "default") {
            contentResolver.update(
                    winkerkEntry.INFO_LOADER_FOTO_URI,
                    values,
                    "${winkerkEntry.INFO_LIDMAAT_GUID} = ?",
                    arrayOf(mLidmaatGUID)
            )
        } else {
            contentResolver.insert(winkerkEntry.INFO_LOADER_FOTO_URI, values)
        }

        val id = current_id
        val memberValues = ContentValues().apply { put(winkerkEntry.LIDMATE_PICTUREPATH, newPath) }
        val memberUri = ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, id.toLong())
        contentResolver.update(
                memberUri,
                memberValues,
                "${winkerkEntry.LIDMATE_TABLE_NAME}._rowid_ = ?",
                arrayOf(id.toString())
        )
    }
    private fun copyFoto(imageUri: Uri, guid: String?): String {
        if (guid.isNullOrEmpty()) return ""

        // 1. Decode the full image from the URI using InputStream
        val fullBitmap =
                try {
                    contentResolver.openInputStream(imageUri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode image from URI", e)
                    null
                } ?: return ""

        // 2. Generate and save thumbnail in app's cache directory
        val width = winkerkEntry.THUMBSIZE
        val height = winkerkEntry.THUMBSIZE
        val thumbBitmap = ThumbnailUtils.extractThumbnail(fullBitmap, width, height)

        val cacheDir = File(winkerkEntry.getCacheDir(this))
        val thumbFile = File(cacheDir, "$guid.png")
        if (thumbFile.exists()) thumbFile.delete()
        try {
            FileOutputStream(thumbFile).use { out ->
                thumbBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail save error", e)
        }
        thumbBitmap.recycle()

        // 3. Save full-size image to external app-specific directory
        val externalDir = getExternalFilesDir(null)
        if (externalDir != null) {
            val photoDir = File(externalDir, "photos")
            if (!photoDir.exists()) photoDir.mkdirs()
            val photoFile = File(photoDir, "$guid.jpg")
            if (photoFile.exists()) photoFile.delete()
            try {
                FileOutputStream(photoFile).use { out ->
                    fullBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Full photo save error", e)
            }
        }
        fullBitmap.recycle()

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
                    val setForceIcons =
                            classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
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
            val imageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photo)
            mImageUri = imageUri
            mCurrentPicUri = imageUri.path
            takePictureLauncher.launch(imageUri)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Throws(Exception::class)
    private fun createTemporaryFile(part: String, ext: String): File {
        val tempDir = File("${winkerkEntry.getFotoDir(this)}/.temp/")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return File.createTempFile(part, ext, tempDir)
    }

    override fun onDestroy() {
        super.onDestroy()
        mImageUri = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return false
    }
}
