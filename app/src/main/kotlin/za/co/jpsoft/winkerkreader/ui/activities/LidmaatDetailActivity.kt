package za.co.jpsoft.winkerkreader.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.WinkerkContract.PREFS_USER_INFO
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry
import za.co.jpsoft.winkerkreader.data.models.FamilyMemberItem
import za.co.jpsoft.winkerkreader.data.models.MemberDetailItem
import za.co.jpsoft.winkerkreader.databinding.LidmaatDetailBinding
import za.co.jpsoft.winkerkreader.ui.adapters.SpinnerAdapter
import za.co.jpsoft.winkerkreader.ui.bottomsheets.StelHerinneringBottomSheet
import za.co.jpsoft.winkerkreader.ui.bottomsheets.VoegNotaByBottomSheet
import za.co.jpsoft.winkerkreader.ui.controllers.LidmaatPastoralSectionController
import za.co.jpsoft.winkerkreader.ui.controllers.MemberPhotoController
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailPastoralViewModelFactory
import za.co.jpsoft.winkerkreader.ui.viewmodels.LidmaatDetailViewModel
import za.co.jpsoft.winkerkreader.utils.MainNavigationController
import za.co.jpsoft.winkerkreader.utils.MemberUtils
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import java.io.File


class LidmaatDetailActivity : BaseActivity() {

    companion object {
        private const val TAG = "LidmaatDetailActivity"
        private const val STATE_IMAGE_URI = "image_uri"

        const val EXTRA_MEMBER_GUID = "memberGUID"


    }

    private val navigationController by lazy { MainNavigationController(this) }
    private lateinit var binding: LidmaatDetailBinding
    private lateinit var settingsManager: SettingsManager

    private var current_id = 0
    private var mLidmaatGUID: String? = null
    private var mStraatAdres: String = ""
    private var mPosAdres: String = ""
    private var recordStatus: String = "0"
    private lateinit var viewModel: LidmaatDetailViewModel

    private val huwelikStatusArray =
        arrayOf("Getroud", "Ongetroud", "Geskei", "Weduwee", "Wewenaar", "Onbekend")
    private val geslagteArray = arrayOf("Vroulik", "Manlik")
    private val geslagPrente = intArrayOf(R.drawable.kvrou, R.drawable.kman)

    private var mGeslagB = ""
    private var mHuwelikstatus = "Ongetroud"
    private lateinit var mCurrentLidmaatUri: Uri
    private lateinit var photoController: MemberPhotoController
    private lateinit var pastoralSectionController: LidmaatPastoralSectionController

    private val pastoralViewModel: LidmaatDetailPastoralViewModel by viewModels {
        val guid = intent.getStringExtra(EXTRA_MEMBER_GUID) ?: ""
        if (BuildConfig.DEBUG) Log.d(TAG, "Pastoral ViewModel GUID: '$guid'")
        LidmaatDetailPastoralViewModelFactory(this, guid)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        photoController.pendingImageUri?.let { outState.putString(STATE_IMAGE_URI, it.toString()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager.getInstance(this)
        binding = LidmaatDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailScroll) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }

        photoController = MemberPhotoController(
            activity = this,
            binding = binding,
            getMemberGuid = { mLidmaatGUID },
            getCurrentId = { current_id }
        )

        savedInstanceState?.getString(STATE_IMAGE_URI)?.let { uriString ->
            photoController.pendingImageUri = uriString.toUri()
        }

        binding.detailIndeterminateBar.visibility = View.GONE
        binding.detailIndeterminateBar2.visibility = View.GONE

        binding.detailMylpaleBlock2.visibility = View.GONE
        binding.detailGroepBlockm.visibility = View.GONE
        binding.detailMeelewingBlock.visibility = View.GONE
        binding.detailPassieBlock.visibility = View.GONE
        binding.detailGawesBlock.visibility = View.GONE

        mCurrentLidmaatUri = intent.data ?: run {
            val guid = intent.getStringExtra(EXTRA_MEMBER_GUID)
                ?: intent.getStringExtra("MEMBER_GUID")
            if (guid != null) {
                val id = getIdFromGuid(guid)
                if (id != -1L) {
                    ContentUris.withAppendedId(winkerkEntry.CONTENT_URI, id)
                } else {
                    throw IllegalArgumentException("Cannot resolve member GUID: $guid")
                }
            } else {
                throw IllegalArgumentException("No data URI and no MEMBER_GUID provided")
            }
        }

        recordStatus = intent.getStringExtra("RECORD_STATUS") ?: "0"

        viewModel = ViewModelProvider(this)[LidmaatDetailViewModel::class.java]

        val guid = intent.getStringExtra(EXTRA_MEMBER_GUID) ?: intent.getStringExtra("MEMBER_GUID")
        if (!guid.isNullOrEmpty()) {
            viewModel.loadMemberByGuid(guid, recordStatus)
        } else if (intent.data != null) {
            viewModel.loadMember(intent.data!!, recordStatus)
        } else {
            throw IllegalArgumentException("No MEMBER_GUID provided")
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.detailIndeterminateBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.memberDetail.observe(this) { item ->
            if (item != null) {
                displayMemberData(item)
                if (item.familyHeadGuid.isNotEmpty()) {
                    viewModel.loadFamily(item.familyHeadGuid, recordStatus)
                }

                // ─── Instantiate pastoral section controller only once ───
                if (!::pastoralSectionController.isInitialized) {
                    pastoralSectionController = LidmaatPastoralSectionController(
                        activity = this,
                        binding = binding,
                        memberGuid = mLidmaatGUID ?: "",
                        familyHeadGuid = item.familyHeadGuid.ifBlank { null },
                        memberDisplayName = "${item.name} ${item.surname}".trim(),
                        memberSurname = item.surname.ifBlank { null },
                        memberGivenName = item.name.ifBlank { null },
                        pastoralViewModel = pastoralViewModel
                    )
                    pastoralSectionController.setup()
                }
            }
        }

        viewModel.familyMembers.observe(this) { members ->
            if (members.isNotEmpty()) {
                displayFamily(members)
            }
        }

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
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::pastoralSectionController.isInitialized) {
            pastoralSectionController.cleanup()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_lidmaat_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }

        if (item.itemId == R.id.action_stel_herinnering) {
            StelHerinneringBottomSheet.newInstance(mLidmaatGUID)
                .show(supportFragmentManager, StelHerinneringBottomSheet.TAG)
            return true
        }

        return false
    }

    // ─── Helper: get member ID from GUID ─────────────────────────────────────

    private fun getIdFromGuid(memberGuid: String): Long {
        val fullQuery = """
            SELECT _rowid_ FROM ${winkerkEntry.LIDMATE_TABLE_NAME}
            WHERE ${winkerkEntry.LIDMATE_LIDMAATGUID} = ?
        """.trimIndent()
        val cursor = contentResolver.query(
            winkerkEntry.CONTENT_URI,
            null,
            fullQuery,
            arrayOf(memberGuid),
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getLong(0) else -1L
        } ?: -1L
    }

    // ─── UI Initialisation ────────────────────────────────────────────────────

    private fun initializeViews() {
        // ─── Toolbar ─────────────────────────────────────────────────────────────

        binding.detailToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        val fieldsToDisable = listOf(
            binding.detailNoemnaam,
            binding.detailVan,
            binding.detailNooiensvan,
            binding.detailWyk,
            binding.detailPosadres,
            binding.detailBeroep,
            binding.detailWerkgewer
        )
        fieldsToDisable.forEach { it.isEnabled = false }

        binding.huwelikstatus.isEnabled = false
        binding.geslag.isEnabled = false

        binding.detailGesinBlock.visibility = View.GONE

        // Initially hide the mylpale content (will be shown when data exists)
        binding.detailMylpaleBlock.visibility = View.GONE
    }

    private fun setupListeners() {
        binding.detailKontakFoto.setOnClickListener { photoController.showImagePopup() }

        binding.buttonWysig.apply {
            isFocusable = true
            isClickable = true
            setOnClickListener { onWysigClick() }
        }

        binding.detailStraatadresBlock.setOnClickListener { openMapForAddress() }

        // ─── Quick action row (new) ──────────────────────────────────────────
        binding.quickActionBel.setOnClickListener {
            MemberUtils.callPhone(this, binding.detailSelfoon.text.toString())
        }
        binding.quickActionWhatsapp.setOnClickListener {
            MemberUtils.sendWhatsApp(this, binding.detailSelfoon.text.toString(), 1)
        }
        binding.quickActionSms.setOnClickListener {
            MemberUtils.sendSms(this, binding.detailSelfoon.text.toString())
        }
        binding.quickActionEpos.setOnClickListener {
            MemberUtils.sendEmail(this, binding.detailEpos.text.toString())
        }
        binding.quickActionKalender.setOnClickListener {
            viewModel.memberDetail.value?.let { member ->
                try {
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(
                            CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                            System.currentTimeMillis()
                        )
                        putExtra(
                            CalendarContract.EXTRA_EVENT_END_TIME,
                            System.currentTimeMillis() + 60 * 60 * 1000
                        )
                        putExtra(
                            CalendarContract.Events.TITLE,
                            "${member.name} ${member.surname}".trim()
                        )
                        putExtra(CalendarContract.Events.DESCRIPTION, buildString {
                            member.birthday?.takeIf { it.isNotEmpty() }
                                ?.let { append("Geboortedatum: $it\n") }
                            member.cellphone?.takeIf { it.isNotEmpty() }
                                ?.let { append("Selfoon: $it\n") }
                            member.email?.takeIf { it.isNotEmpty() }?.let { append("Epos: $it\n") }
                            member.streetAddress?.takeIf { it.isNotEmpty() }
                                ?.let { append("Adres: $it\n") }
                        }.trimEnd())
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Kon nie kalender oopmaak nie", Toast.LENGTH_SHORT).show()
                }
            }
        }
// ✅ Pastoral Reminder quick action
        binding.quickActionHerinnering.setOnClickListener {
            mLidmaatGUID?.let { guid ->
                StelHerinneringBottomSheet.newInstance(guid)
                    .show(supportFragmentManager, StelHerinneringBottomSheet.TAG)
            } ?: run {
                Toast.makeText(this, "Geen lidmaat GUID beskikbaar", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Notes quick action
        binding.quickActionNotas.setOnClickListener {
            mLidmaatGUID?.let { guid ->
                // Expand the notes section if collapsed
                expandNotasSection()

                // Focus on the notes section
                binding.layoutDetailNotasHeader.performClick()

                // Open new note dialog
                VoegNotaByBottomSheet.newInstance(guid)
                    .show(supportFragmentManager, VoegNotaByBottomSheet.TAG)
            } ?: run {
                Toast.makeText(this, "Geen lidmaat GUID beskikbaar", Toast.LENGTH_SHORT).show()
            }
        }
        // ─── Collapsible Mylpale section ─────────────────────────────────────
        binding.cardMylpaleHeader.setOnClickListener {
            toggleMylpaleSection()
        }

        // ─── Old icon listeners (kept for compatibility) ─────────────────────
        binding.detailSelfoonIcon.setOnClickListener {
            MemberUtils.callPhone(this, binding.detailSelfoon.text.toString())
        }
        binding.detailLandlynIcon.setOnClickListener {
            MemberUtils.callPhone(this, binding.detailTelefoon.text.toString())
        }
        binding.detailWhatsappIcon.setOnClickListener {
            MemberUtils.sendWhatsApp(this, binding.detailSelfoon.text.toString(), 1)
        }
        binding.detailEmailIcon.setOnClickListener {
            MemberUtils.sendEmail(this, binding.detailEpos.text.toString())
        }
        binding.detailSmsIcon.setOnClickListener {
            MemberUtils.sendSms(this, binding.detailSelfoon.text.toString())
        }

        setupCopyOnLongClick()
    }
// ─── Helper to expand notes section ────────────────────────────────────

    private fun expandNotasSection() {
        val content = binding.layoutDetailNotasInhoud
        val chevron = binding.ivDetailNotasChevron

        if (content.visibility != View.VISIBLE) {
            content.visibility = View.VISIBLE
            chevron.animate().rotation(180f).duration = 200
        }
    }

    private fun toggleMylpaleSection() {
        val content = binding.detailMylpaleBlock
        val chevron = binding.cardMylpaleChevron
        if (content.visibility == View.VISIBLE) {
            content.visibility = View.GONE
            chevron.animate().rotation(0f).duration = 200
        } else {
            content.visibility = View.VISIBLE
            chevron.animate().rotation(180f).duration = 200
        }
    }

    private fun setupCopyOnLongClick() {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val fields = listOf(
            binding.detailVollename,
            binding.detailLidmaatstatus,
            binding.detailGeboortedatum,
            binding.detailSelfoon,
            binding.detailTelefoon,
            binding.detailEpos,
            binding.detailStraatadres
        )
        for (view in fields) {
            view.isEnabled = true
            view.isFocusable = false
            view.isFocusableInTouchMode = false
            view.isLongClickable = true

            view.setOnLongClickListener {
                val text = view.text.toString()
                if (text.isNotEmpty()) {
                    val clip = ClipData.newPlainText("text", text)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(this, "Gekopieer: $text", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Geen teks om te kopieer", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
    }

    // ─── Display member data ─────────────────────────────────────────────────

    private fun displayMemberData(item: MemberDetailItem) {
        current_id = item.id
        mLidmaatGUID = item.guid

        photoController.loadMemberPhoto(item.guid)

        // ─── Header ──────────────────────────────────────────
        binding.detailHeaderNaam.text = "${item.name} ${item.surname}".trim()
        binding.chipOuderdom.text = if (item.age < 0) "?" else "${item.age} jaar"
        binding.chipWyk.text = item.ward
        binding.chipWyk.visibility = if (item.ward.isNotEmpty()) View.VISIBLE else View.GONE
        binding.chipLidmaatstatus.text = item.memberStatus

        // Gemeente – use member's own gemeente, fallback to user's
        binding.detailGemeentenaam.text = item.gemeente?.takeIf { it.isNotEmpty() }
            ?: settingsManager.gemeenteNaam

        // ─── Quick actions enable ──────────────────────────
        binding.quickActionBel.isEnabled = item.cellphone.isNotEmpty()
        binding.quickActionWhatsapp.isEnabled = item.cellphone.isNotEmpty()
        binding.quickActionSms.isEnabled = item.cellphone.isNotEmpty()
        binding.quickActionEpos.isEnabled = item.email.isNotEmpty()
        // ✅ Pastoral actions - always enabled since they open dialogs
        binding.quickActionHerinnering.isEnabled = true
        binding.quickActionNotas.isEnabled = true
        // ─── Fill fields ──────────────────────────────────
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
//        binding.detailLidmaatstatus.setText(item.memberStatus)
//
//        binding.detailLidmaatstatus.setBackgroundColor(
//            when (item.certificateStatus) {
//                "Ontvang" -> Color.WHITE
//                "Aangevra" -> Color.GREEN
//                "Nie Aangevra" -> Color.CYAN
//                else -> Color.WHITE
//            }
//        )
// In displayMemberData()
        when (item.certificateStatus) {
            "Ontvang" -> {
                binding.detailLidmaatstatus.setText("✓ " + item.memberStatus)
                binding.detailLidmaatstatus.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.md_theme_primary
                    )
                )
//                binding.detailLidmaatstatus.setCompoundDrawablesWithIntrinsicBounds(
//                    R.drawable.ic_check_circle, 0, 0, 0
//                )
            }

            "Aangevra" -> {
                binding.detailLidmaatstatus.setText("⏳ " + item.memberStatus)
                binding.detailLidmaatstatus.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.md_theme_secondary
                    )
                )
//                binding.detailLidmaatstatus.setCompoundDrawablesWithIntrinsicBounds(
//                    R.drawable.ic_pending, 0, 0, 0
//                )
            }

            "Nie Aangevra" -> {
                binding.detailLidmaatstatus.setText("✕ " + item.memberStatus)
                binding.detailLidmaatstatus.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.md_theme_error
                    )
                )
//                binding.detailLidmaatstatus.setCompoundDrawablesWithIntrinsicBounds(
//                    R.drawable.ic_error, 0, 0, 0
//                )
            }

            else -> {
                binding.detailLidmaatstatus.setText("? " + item.memberStatus)
                binding.detailLidmaatstatus.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.md_theme_onSurface
                    )
                )
//                binding.detailLidmaatstatus.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
        }
        // ─── Block visibility (with address duplication fix) ──
        val street = item.streetAddress ?: ""
        val postal = item.postalAddress ?: ""

        binding.detailSelfoonBlock.visibility =
            if (item.cellphone.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailTelefoonBlock.visibility =
            if (item.landline.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailEposBlock.visibility =
            if (item.email.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailNooiensvanBlock.visibility =
            if (item.maidenName.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailBeroepBlock.visibility =
            if (item.profession.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailWerkgewerBlock.visibility =
            if (item.employer.isNotEmpty()) View.VISIBLE else View.GONE
        binding.detailStraatadresBlock.visibility =
            if (street.isNotEmpty()) View.VISIBLE else View.GONE
        // Show postal only if it exists and is different from street
        binding.detailPosadresBlock.visibility =
            if (postal.isNotEmpty() && postal != street) View.VISIBLE else View.GONE

        // ─── Manage dividers between visible contact blocks ──
        val blockDividerPairs = listOf(
            binding.detailSelfoonBlock to binding.dividerSelfoon,
            binding.detailTelefoonBlock to binding.dividerTelefoon,
            binding.detailEposBlock to binding.dividerEpos,
            binding.detailStraatadresBlock to binding.dividerStraatadres
        )
        blockDividerPairs.forEach { (_, divider) -> divider.visibility = View.GONE }
        val visibleBlocks =
            blockDividerPairs.map { it.first }.filter { it.visibility == View.VISIBLE }
        for (i in 0 until visibleBlocks.size - 1) {
            val block = visibleBlocks[i]
            val divider = blockDividerPairs.find { it.first == block }?.second
            divider?.visibility = View.VISIBLE
        }

        // ─── Spinners ──────────────────────────────────────
        val geslagAdapter = SpinnerAdapter(applicationContext, geslagPrente, null)
        binding.geslag.adapter = geslagAdapter
        binding.geslag.setSelection(if (item.gender == "Manlik") 1 else 0)
        mGeslagB = item.gender

        val huwelikAdapter = SpinnerAdapter(applicationContext, null, huwelikStatusArray)
        binding.huwelikstatus.adapter = huwelikAdapter
        val huwPos = huwelikStatusArray.indexOfFirst { it == item.marriageStatus }
        if (huwPos >= 0) binding.huwelikstatus.setSelection(huwPos)
        mHuwelikstatus = item.marriageStatus

        // ─── Milestones ────────────────────────────────────
        loadMilestones(item)
    }

    private fun displayFamily(members: List<FamilyMemberItem>) {
        val container = binding.detailGesinContent

        // Remove all views except the header (which is at index 0)
        val childCount = container.childCount
        if (childCount > 1) {
            container.removeViews(1, childCount - 1)
        }

        for (member in members) {
            if (member.id == current_id) continue

            val ageText = if (member.age < 0) "(?)" else "(${member.age})"
            val gesinString = "\n${member.name}\t ${member.surname}\t ${member.birthday} $ageText"

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val fotoFrame = FrameLayout(this)
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(256, 256)
                scaleType = ImageView.ScaleType.FIT_XY
                if (member.picturePath.isNotEmpty()) {
                    val file =
                        File(winkerkEntry.getCacheDir(this@LidmaatDetailActivity) + member.picturePath)
                    if (file.exists()) {
                        Glide.with(binding.detailKontakFoto)
                            .load(file)
                            .override(256, 256)
                            .centerCrop()
                            .placeholder(R.drawable.clipboard)
                            .error(R.drawable.clipboard)
                            .into(binding.detailKontakFoto)
                    } else {
                        setImageResource(R.drawable.clipboard)
                    }
                } else {
                    setImageResource(R.drawable.clipboard)
                }
            }
            // Circular crop
            imageView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            imageView.clipToOutline = true
            fotoFrame.addView(imageView)

            val textView = TextView(this).apply {
                text = gesinString
                setPadding(32, 0, 0, 0)
                TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
                tag = member.id // member.id is Int
                setOnClickListener {
                    // Safely retrieve as Int, convert to Long
                    val gId = (tag as? Int)?.toLong()
                    val guid = member.guid
                    navigationController.navigateToLidmaatDetail(guid, recordStatus, gId)
                    finish()
                }
            }

            rowLayout.addView(fotoFrame)
            rowLayout.addView(textView)
            container.addView(rowLayout)
        }

        // Show the card only if there are other family members (excluding self)
        binding.detailGesinBlock.visibility = if (members.size > 1) View.VISIBLE else View.GONE
    }

    private fun loadMilestones(item: MemberDetailItem) {
        // Use binding fields instead of findViewById
        val mylpaleBlock = binding.detailMylpaleBlock          // LinearLayout
        val mylpaleBlock2 = binding.detailMylpaleBlock2        // MaterialCardView

        mylpaleBlock.removeAllViews()
        mylpaleBlock2.visibility = View.GONE
        mylpaleBlock.visibility = View.GONE   // hide content initially

        var hasMilestones = false

        if (item.baptismDate.isNotEmpty()) {
            hasMilestones = true
            val doopText = "Doop\t\t\t\t\t(${item.baptismDate})"
            val doopTv = TextView(this).apply {
                text = doopText
                TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
            }
            mylpaleBlock.addView(doopTv)
            if (item.baptismDs.isNotEmpty()) {
                val leraarTv = TextView(this).apply {
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
            hasMilestones = true
            val belyText = "Belydenis van geloof\t\t(${item.confessionDate})"
            val belyTv = TextView(this).apply {
                text = belyText
                TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
            }
            mylpaleBlock.addView(belyTv)
            if (item.confessionDs.isNotEmpty()) {
                val leraarTv = TextView(this).apply {
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
            hasMilestones = true
            var huwelikText = "Huwelik\t\t(${item.marriageDate})"
            if (item.marriageYears >= 0) {
                huwelikText = "$huwelikText : ${item.marriageYears} jaar)"
            }
            val huwelikTv = TextView(this).apply {
                text = huwelikText
                TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Medium)
            }
            mylpaleBlock.addView(huwelikTv)
        }

        if (hasMilestones) {
            mylpaleBlock2.visibility = View.VISIBLE
            mylpaleBlock.visibility = View.VISIBLE   // show content
            binding.cardMylpaleChevron.rotation = 180f
        } else {
            mylpaleBlock2.visibility = View.GONE
        }
    }

    // ─── Edit / update member ─────────────────────────────────────────────────

    private fun onWysigClick() {
        if (binding.buttonWysig.text == getString(R.string.wysig)) {
            enableEditing(true)
            binding.buttonWysig.text = getString(R.string.stoor)
            binding.buttonWysig.setBackgroundTintList(ColorStateList.valueOf(Color.RED))
            mStraatAdres = binding.detailStraatadres.text.toString()
            mPosAdres = binding.detailPosadres.text.toString()
            showSoftKeyboard(binding.buttonWysig)
        } else {
            // FIRST: save while fields still hold edited values
            viewModel.memberDetail.value?.let { wysigLidmaatData(it) }
            // THEN: disable editing
            enableEditing(false)
            binding.buttonWysig.text = getString(R.string.wysig)
            binding.buttonWysig.setBackgroundTintList(ColorStateList.valueOf("#0A064F".toColorInt()))
            hideSoftKeyboard()
            // FINALLY: reload from DB so cleared/changed fields reflect correctly
            mLidmaatGUID?.let { viewModel.loadMemberByGuid(it, recordStatus) }
        }
    }

    private fun enableEditing(enable: Boolean) {
        val fields = listOf(
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
        fields.forEach {
            it.isEnabled = enable
            it.isFocusable = enable
            it.isFocusableInTouchMode = enable
        }
        binding.huwelikstatus.isEnabled = enable
        binding.geslag.isEnabled = enable

        val conditionalBlocks = listOf(
            binding.detailNooiensvanBlock,
            binding.detailBeroepBlock,
            binding.detailWerkgewerBlock,
            binding.detailPosadresBlock,
            binding.detailSelfoonBlock,
            binding.detailTelefoonBlock,
            binding.detailEposBlock,
            binding.detailStraatadresBlock
        )

        if (enable) {
            conditionalBlocks.forEach { it.visibility = View.VISIBLE }
        } else {
            viewModel.memberDetail.value?.let { displayMemberData(it) }
        }
    }

    private fun wysigLidmaatData(item: MemberDetailItem) {
        val id = item.id
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

        checkAndPut(
            winkerkEntry.LIDMATE_NOEMNAAM,
            binding.detailNoemnaam.text.toString(),
            item.name
        )
        checkAndPut(winkerkEntry.LIDMATE_VAN, binding.detailVan.text.toString(), item.surname)
        checkAndPut(
            winkerkEntry.LIDMATE_VOORNAME,
            binding.detailVollename.text.toString(),
            item.fullNames
        )
        checkAndPut(
            winkerkEntry.LIDMATE_SELFOON,
            binding.detailSelfoon.text.toString(),
            item.cellphone
        )
        checkAndPut(
            winkerkEntry.LIDMATE_LANDLYN,
            binding.detailTelefoon.text.toString(),
            item.landline
        )
        checkAndPut(winkerkEntry.LIDMATE_WYK, binding.detailWyk.text.toString(), item.ward)
        checkAndPut(
            winkerkEntry.LIDMATE_LIDMAATSTATUS,
            binding.detailLidmaatstatus.text.toString()
                .removePrefix("✓ ")
                .removePrefix("⏳ ")
                .removePrefix("✕ ")
                .removePrefix("? "),
            item.memberStatus
        )
        checkAndPut(
            winkerkEntry.LIDMATE_GEBOORTEDATUM,
            binding.detailGeboortedatum.text.toString(),
            item.birthday
        )
        checkAndPut(winkerkEntry.LIDMATE_EPOS, binding.detailEpos.text.toString(), item.email)
        checkAndPut(
            winkerkEntry.LIDMATE_NOOIENSVAN,
            binding.detailNooiensvan.text.toString(),
            item.maidenName
        )
        checkAndPut(
            winkerkEntry.LIDMATE_BEROEP,
            binding.detailBeroep.text.toString(),
            item.profession
        )
        checkAndPut(
            winkerkEntry.LIDMATE_WERKGEWER,
            binding.detailWerkgewer.text.toString(),
            item.employer
        )

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
        emailUrl = when (gemeente) {
            settingsManager.gemeenteNaam -> settingsManager.gemeenteEpos
            settingsManager.gemeente2Naam -> settingsManager.gemeente2Epos
            settingsManager.gemeente3Naam -> settingsManager.gemeente3Epos
            else -> ""
        }

        val prefs = getSharedPreferences(PREFS_USER_INFO, MODE_PRIVATE)
        val eposHtmlEnabled = prefs.getBoolean("EposHtml", false)

        val sendIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailUrl))
            if (eposHtmlEnabled) {
                val html: Spanned = Html.fromHtml(emailHtml, Html.FROM_HTML_MODE_LEGACY)
                putExtra(Intent.EXTRA_TEXT, html)
            } else {
                putExtra(Intent.EXTRA_TEXT, emailText)
            }
        }

        try {
            startActivity(sendIntent)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Email intent failed", e)
        }
    }

    private fun openMapForAddress() {
        val address = binding.detailStraatadres.text.toString()
        if (address.isNotEmpty()) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = "${binding.detailNoemnaam.text} ${binding.detailVan.text}\r\n$address"
            clipboard.setPrimaryClip(ClipData.newPlainText("text", clipData))
            Toast.makeText(this, clipData, Toast.LENGTH_SHORT).show()

            val encoded = address.replace("\n", "%20")
                .replace("\t", "%20")
                .replace("\r", "%2C")
                .replace(" ", "%20")
            val mapUri = "geo:0,0?q=$encoded"
            startActivity(Intent(Intent.ACTION_VIEW, mapUri.toUri()))
        }
    }

    // ─── Keyboard helpers ────────────────────────────────────────────────────

    private fun hideSoftKeyboard() {
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun showSoftKeyboard(view: View) {
        view.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, 0)
    }

    private fun buildEventDescription(member: MemberDetailItem): String {
        return buildString {
            member.birthday?.takeIf { it.isNotEmpty() }?.let { append("Geboortedatum: $it\n") }
            member.cellphone?.takeIf { it.isNotEmpty() }?.let { append("Selfoon: $it\n") }
            member.email?.takeIf { it.isNotEmpty() }?.let { append("Epos: $it\n") }
            member.streetAddress?.takeIf { it.isNotEmpty() }?.let { append("Adres: $it\n") }
        }.trimEnd()
    }
}