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
import com.bumptech.glide.Glide
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
import za.co.jpsoft.winkerkreader.databinding.LidmaatDetailBinding
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

    private lateinit var binding: LidmaatDetailBinding
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
        binding = LidmaatDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Removed AppSessionState.deviceId assignment
        val deviceId = DeviceIdManager.getDeviceId(this)

        binding.detailIndeterminateBar.visibility = View.GONE
        binding.detailIndeterminateBar2.visibility = View.GONE

        binding.detailMylpaleBlock2.visibility = View.GONE
        binding.detailGroepBlockm.visibility = View.GONE
        binding.detailMeelewingBlock.visibility = View.GONE
        binding.detailPassieBlock.visibility = View.GONE
        binding.detailGawesBlock.visibility = View.GONE

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

        binding.detailGemeentenaam.text = settingsManager.gemeenteNaam
        binding.detailGemeentenaam.isSelected = true

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
        listOf(
                        binding.detailNoemnaam,
                        binding.detailVan,
                        binding.detailNooiensvan,
                        binding.detailVollename,
                        binding.detailSelfoon,
                        binding.detailTelefoon,
                        binding.detailWyk,
                        binding.detailGeboortedatum,
                        binding.detailStraatadres,
                        binding.detailPosadres,
                        binding.detailEpos,
                        binding.detailBeroep,
                        binding.detailWerkgewer,
                        binding.detailLidmaatstatus
                )
                .forEach { it.isEnabled = false }
        binding.huwelikstatus.isEnabled = false
        binding.geslag.isEnabled = false

        binding.detailGesinBlock.visibility = View.GONE
    }

    private fun setupListeners() {
        binding.detailKontakFoto.setOnClickListener { showImagePopup() }

        binding.buttonWysig.apply {
            isFocusable = true
            isClickable = true
            setOnClickListener { onWysigClick() }
        }

        binding.detailStraatadresBlock.setOnClickListener { openMapForAddress() }

        binding.detailSelfoonIcon.setOnClickListener { dialNumber(binding.detailSelfoon.text.toString()) }
        binding.detailLandlynIcon.setOnClickListener { dialNumber(binding.detailTelefoon.text.toString()) }
        binding.detailWhatsappIcon.setOnClickListener { openWhatsApp() }
        binding.detailEmailIcon.setOnClickListener { sendEmail() }
        binding.detailSmsIcon.setOnClickListener { openSms() }
    }

    private fun showImagePopup() {
        val popup = PopupMenu(this, binding.detailKontakFoto)
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
        if (binding.buttonWysig.text == getString(R.string.wysig)) {
            enableEditing(true)
            binding.buttonWysig.text = getString(R.string.stoor)
            binding.buttonWysig.setBackgroundColor(Color.RED)
            mStraatAdres = binding.detailStraatadres.text.toString()
            mPosAdres = binding.detailPosadres.text.toString()
            showSoftKeyboard(binding.buttonWysig)
        } else {
            enableEditing(false)
            binding.buttonWysig.text = getString(R.string.wysig)
            binding.buttonWysig.setBackgroundColor(Color.parseColor("#0A064F"))
            viewModel.memberDetail.value?.let { wysigLidmaatData(it) }
            hideSoftKeyboard()
        }
    }

    private fun enableEditing(enable: Boolean) {
        listOf(
                        binding.detailNoemnaam,
                        binding.detailVan,
                        binding.detailNooiensvan,
                        binding.detailVollename,
                        binding.detailSelfoon,
                        binding.detailTelefoon,
                        binding.detailWyk,
                        binding.detailGeboortedatum,
                        binding.detailStraatadres,
                        binding.detailPosadres,
                        binding.detailEpos,
                        binding.detailBeroep,
                        binding.detailWerkgewer,
                        binding.detailLidmaatstatus
                )
                .forEach { it.isEnabled = enable }
        binding.huwelikstatus.isEnabled = enable
        binding.geslag.isEnabled = enable

        if (enable) {
            binding.detailSelfoonBlock.visibility = View.VISIBLE
            binding.detailTelefoonBlock.visibility = View.VISIBLE
            binding.detailStraatadresBlock.visibility = View.VISIBLE
            binding.detailPosadresBlock.visibility = View.VISIBLE
            binding.detailEposBlock.visibility = View.VISIBLE
            binding.detailNooiensvanBlock.visibility = View.VISIBLE
            binding.detailBeroepBlock.visibility = View.VISIBLE
            binding.detailWerkgewerBlock.visibility = View.VISIBLE
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
        val number = binding.detailSelfoon.text.toString()
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
        val email = binding.detailEpos.text.toString()
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
        val number = binding.detailSelfoon.text.toString()
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
        val address = binding.detailStraatadres.text.toString()
        if (address.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = "${binding.detailNoemnaam.text} ${binding.detailVan.text}\r\n$address"
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

        binding.detailNoemnaam.setText(item.name)
        binding.detailVan.setText(item.surname)
        binding.detailVollename.setText(item.fullNames)
        binding.detailNooiensvan.setText(item.maidenName)
        binding.detailSelfoon.setText(item.cellphone)
        binding.detailTelefoon.setText(item.landline)
        binding.detailWyk.setText(item.ward)
        binding.detailGeboortedatum.setText(item.birthday)
        binding.detailJareoud.text = if (item.age < 0) "(?)" else "(${item.age})"
        binding.detailStraatadres.setText(item.streetAddress)
        binding.detailPosadres.setText(item.postalAddress)
        binding.detailEpos.setText(item.email)
        binding.detailBeroep.setText(item.profession)
        binding.detailWerkgewer.setText(item.employer)
        binding.detailLidmaatstatus.setText(item.memberStatus)

        binding.detailLidmaatstatus.setBackgroundColor(
                when (item.certificateStatus) {
                    "Ontvang" -> Color.WHITE
                    "Aangevra" -> Color.GREEN
                    "Nie Aangevra" -> Color.CYAN
                    else -> Color.WHITE
                }
        )

        binding.detailSelfoonBlock.visibility = if (item.cellphone.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailTelefoonBlock.visibility = if (item.landline.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailEposBlock.visibility = if (item.email.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailNooiensvanBlock.visibility = if (item.maidenName.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailBeroepBlock.visibility = if (item.profession.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailWerkgewerBlock.visibility = if (item.employer.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailStraatadresBlock.visibility = if (item.streetAddress.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailPosadresBlock.visibility = if (item.postalAddress.isNotEmpty()) View.VISIBLE else View.GONE

        val geslagAdapter = SpinnerAdapter(applicationContext, geslagPrente, null)
        binding.geslag.adapter = geslagAdapter
        binding.geslag.setSelection(if (item.gender == "Manlik") 1 else 0)
        mGeslagB = item.gender

        val huwelikAdapter = SpinnerAdapter(applicationContext, null, huwelikStatusArray)
        binding.huwelikstatus.adapter = huwelikAdapter
        val huwPos = huwelikStatusArray.indexOfFirst { it == item.marriageStatus }
        if (huwPos >= 0) binding.huwelikstatus.setSelection(huwPos)
        mHuwelikstatus = item.marriageStatus

        loadMilestones(item)
    }

    private fun loadMemberPhoto(guid: String?) {
        if (guid == null) {
            setDefaultPhoto()
            return
        }
        val syncedPath = getSyncedPhotoPath(guid)
        if (syncedPath != null) {
            val file = File(syncedPath)
            if (file.exists()) {
                // Set desired dimensions (200dp converted to pixels)
                val pixels = (200 * resources.displayMetrics.density + 0.5f).toInt()
                binding.detailKontakFoto.layoutParams.height = pixels
                binding.detailKontakFoto.layoutParams.width = pixels
                binding.detailKontakFoto.requestLayout()

                Glide.with(this).load(file).override(pixels, pixels).centerCrop().placeholder(R.drawable.kontaks).error(R.drawable.kontaks).into(binding.detailKontakFoto)

                binding.detailKontakFoto.tag = "synced"
            } else {
                setDefaultPhoto()
            }
        } else {
            setDefaultPhoto()
        }
    }

    private fun displayFamily(members: List<FamilyMemberItem>) {
        binding.detailGesinBlock.visibility = View.VISIBLE
        binding.detailGesinBlock.removeAllViews()

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
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(256, 256)
                scaleType = ImageView.ScaleType.FIT_XY
                if (member.picturePath.isNotEmpty()) {
                    val file = File(winkerkEntry.getCacheDir(this@LidmaatDetailActivity) + member.picturePath)
                    if (file.exists()) {
                        Glide.with(this@LidmaatDetailActivity)
                            .load(file)
                            .override(256, 256)
                            .centerCrop()
                            .placeholder(R.drawable.clipboard)
                            .error(R.drawable.clipboard)
                            .into(this)
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
            binding.detailGesinBlock.addView(innerLayout)
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
        binding.detailKontakFoto.layoutParams.height = pixels
        binding.detailKontakFoto.layoutParams.width = pixels
        binding.detailKontakFoto.requestLayout()
        binding.detailKontakFoto.setImageResource(R.drawable.kontaks)
        binding.detailKontakFoto.tag = "default"
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

        checkAndPut(winkerkEntry.LIDMATE_NOEMNAAM, binding.detailNoemnaam.text.toString(), item.name)
        checkAndPut(winkerkEntry.LIDMATE_VAN, binding.detailVan.text.toString(), item.surname)
        checkAndPut(winkerkEntry.LIDMATE_VOORNAME, binding.detailVollename.text.toString(), item.fullNames)
        checkAndPut(winkerkEntry.LIDMATE_SELFOON, binding.detailSelfoon.text.toString(), item.cellphone)
        checkAndPut(winkerkEntry.LIDMATE_LANDLYN, binding.detailTelefoon.text.toString(), item.landline)
        checkAndPut(winkerkEntry.LIDMATE_WYK, binding.detailWyk.text.toString(), item.ward)
        checkAndPut(winkerkEntry.LIDMATE_LIDMAATSTATUS, binding.detailLidmaatstatus.text.toString(), item.memberStatus)
        checkAndPut(winkerkEntry.LIDMATE_GEBOORTEDATUM, binding.detailGeboortedatum.text.toString(), item.birthday)
        checkAndPut(winkerkEntry.LIDMATE_EPOS, binding.detailEpos.text.toString(), item.email)
        checkAndPut(winkerkEntry.LIDMATE_NOOIENSVAN, binding.detailNooiensvan.text.toString(), item.maidenName)
        checkAndPut(winkerkEntry.LIDMATE_BEROEP, binding.detailBeroep.text.toString(), item.profession)
        checkAndPut(winkerkEntry.LIDMATE_WERKGEWER, binding.detailWerkgewer.text.toString(), item.employer)

        val newStraat = binding.detailStraatadres.text.toString()
        if (newStraat != mStraatAdres) {
            values.put(winkerkEntry.LIDMATE_STRAATADRES, newStraat)
            emailHtml += "\r\n<p>${winkerkEntry.LIDMATE_STRAATADRES} : <b><font color='red'>$newStraat</font></b></p>"
            emailText += "\r\n${winkerkEntry.LIDMATE_STRAATADRES} : $newStraat"
        }

        val newPos = binding.detailPosadres.text.toString()
        if (newPos != mPosAdres) {
            values.put(winkerkEntry.LIDMATE_POSADRES, newPos)
            emailHtml += "\r\n<p>${winkerkEntry.LIDMATE_POSADRES} : <b><font color='red'>$newPos</font></b></p>"
            emailText += "\r\n${winkerkEntry.LIDMATE_POSADRES} : $newPos"
        }

        val huwPos = binding.huwelikstatus.selectedItemPosition
        val newHuwelik = huwelikStatusArray[huwPos]
        if (newHuwelik != item.marriageStatus) {
            values.put(winkerkEntry.LIDMATE_HUWELIKSTATUS, newHuwelik)
            emailHtml += "\r\n<p>${winkerkEntry.LIDMATE_HUWELIKSTATUS} : <b><font color='red'>$newHuwelik</font></b></p>"
            emailText += "\r\n${winkerkEntry.LIDMATE_HUWELIKSTATUS} : $newHuwelik"
        }

        val geslagPos = binding.geslag.selectedItemPosition
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
        val newPath = copyFoto(imageUri, mLidmaatGUID)
        if (newPath.isEmpty()) {
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
            return
        }

        // Update UI to show the new photo using Glide
        val pixels = (200 * resources.displayMetrics.density + 0.5f).toInt()
        binding.detailKontakFoto.layoutParams.height = pixels
        binding.detailKontakFoto.layoutParams.width = pixels
        binding.detailKontakFoto.requestLayout()

        val photoFile = File(getExternalFilesDir(null), "photos/$newPath")
        Glide.with(this)
            .load(photoFile)
            .override(pixels, pixels)
            .centerCrop()
            .placeholder(R.drawable.kontaks)
            .error(R.drawable.kontaks)
            .into(binding.detailKontakFoto)

        binding.detailKontakFoto.tag = "synced"

        // Save reference in database (unchanged)
        val values = ContentValues().apply {
            put(winkerkEntry.INFO_FOTO_PATH, newPath)
            put(winkerkEntry.INFO_LIDMAAT_GUID, mLidmaatGUID)
            put(winkerkEntry.INFO_GROUP, "")
        }

        contentResolver.update(
            winkerkEntry.INFO_LOADER_FOTO_URI,
            values,
            "${winkerkEntry.INFO_LIDMAAT_GUID} = ?",
            arrayOf(mLidmaatGUID)
        )

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

        // Decode with inSampleSize=2 to reduce memory for thumbnail creation
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val fullBitmap = contentResolver.openInputStream(imageUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        } ?: return ""

        // Thumbnail creation (unchanged)
        val width = winkerkEntry.THUMBSIZE
        val height = winkerkEntry.THUMBSIZE
        val thumbBitmap = ThumbnailUtils.extractThumbnail(fullBitmap, width, height)
        // ... save thumbBitmap ...
        thumbBitmap.recycle()

        // Save full-size image to external directory (use original quality)
        val externalDir = getExternalFilesDir(null)
        if (externalDir != null) {
            val photoDir = File(externalDir, "photos")
            if (!photoDir.exists()) photoDir.mkdirs()
            val photoFile = File(photoDir, "$guid.jpg")
            if (photoFile.exists()) photoFile.delete()
            contentResolver.openInputStream(imageUri)?.use { inputStream ->
                FileOutputStream(photoFile).use { out ->
                    inputStream.copyTo(out)   // Copy file directly, no decode/encode
                }
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
