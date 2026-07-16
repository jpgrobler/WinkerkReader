package za.co.jpsoft.winkerkreader.ui.adapters

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.data.models.MemberItem
import za.co.jpsoft.winkerkreader.data.repositories.ContactRepository
import za.co.jpsoft.winkerkreader.databinding.ListItem2Binding
import za.co.jpsoft.winkerkreader.databinding.ListItemBinding
import za.co.jpsoft.winkerkreader.utils.PhotoHelper
import za.co.jpsoft.winkerkreader.utils.SettingsManager
import za.co.jpsoft.winkerkreader.utils.Utils.fixphonenumber
import java.util.Locale

class MemberListAdapter(
    private val onItemClick: (view: View, item: MemberItem, position: Int) -> Unit,
    private val onItemLongClick: (item: MemberItem, position: Int) -> Boolean
) : PagingDataAdapter<MemberItem, MemberListAdapter.MemberViewHolder>(DIFF) {

    // Display state – changes require a UI refresh
    private var listView: Int = 2
    private var soekList: Boolean = false
    private var soek: String = ""
    private var recordStatus: String = "0"
    private var sortOrder: String = "VAN"

    private var pendingReminderGuids: Set<String> = emptySet()

    // Collapse state: key = "$sortOrder:$groupValue" -> true = collapsed
    private val collapsedGroups = mutableSetOf<String>()

    // Spannable cache with LRU-like behavior (limit size)
    private val spannableCache = object : LinkedHashMap<String, CharSequence>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CharSequence>): Boolean {
            return size > 100
        }
    }

    companion object {
        private const val TAG = "MemberListAdapter"
        const val VIEW_TYPE_COMPACT = 1
        const val VIEW_TYPE_DETAILED = 2

        private val DIFF = object : DiffUtil.ItemCallback<MemberItem>() {
            override fun areItemsTheSame(oldItem: MemberItem, newItem: MemberItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: MemberItem, newItem: MemberItem) =
                oldItem == newItem
        }

        private val PHOTO_OPTIONS = RequestOptions()
            .centerCrop()
            .skipMemoryCache(false)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .timeout(5000)
    }

    // ================================================================
    // Collapse API
    // ================================================================

    /**
     * Toggle collapse state for a group.
     * @param sortOrder Current sort order (e.g., "VAN", "WYK")
     * @param groupValue The value of the group (e.g., surname first letter, ward name)
     * @param headerPosition The position of the separator header in the list
     */
    fun toggleGroupCollapsed(sortOrder: String, groupValue: String, headerPosition: Int) {
        val key = "$sortOrder:$groupValue"
        val isNowCollapsed = if (!collapsedGroups.add(key)) {
            collapsedGroups.remove(key)
            false
        } else {
            true
        }

        // Find the range of items this separator controls
        val items = getAllItems()
        var end = headerPosition

        // Move forward until we hit the next separator or the end of the list
        while (end + 1 < items.size) {
            val nextItem = items[end + 1]
            // Stop when we reach another separator (the next section header)
            if (nextItem.showSeparator) {
                break
            }
            end++
        }

        // Update the chevron icon for the header
        notifyItemChanged(headerPosition)

        // Hide/show items from header+1 to end (the items in this section)
        // This includes the card views for all items in this section
        if (end > headerPosition) {
            notifyItemRangeChanged(headerPosition + 1, end - headerPosition)
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "toggleGroupCollapsed: $key -> ${if (isNowCollapsed) "collapsed" else "expanded"}, range ${headerPosition + 1}-$end"
            )
        }
    }

    /**
     * Get the group value for an item based on the current sort order.
     * Must match the separator logic in MemberItemSeparator.
     */
    private fun getGroupValueFor(item: MemberItem, sortOrder: String): String {
        return when (sortOrder) {
            "WYK" -> item.ward
            "GESINNE" -> item.familyHead
            "VAN" -> if (item.surname.isNotEmpty()) item.surname.substring(0, 1) else ""
            "ADRES" -> item.address
            "VERJAAR" -> if (item.birthday.length >= 5) item.birthday.substring(3, 5) else ""
            "HUWELIK" -> if (item.weddingDate.length >= 5) item.weddingDate.substring(3, 5) else ""
            "OUDERDOM" -> item.age
            else -> item.surname
        }
    }

    /**
     * Check if an item should be hidden because its group is collapsed.
     * The separator itself is never hidden.
     */
    private fun isItemCollapsed(item: MemberItem, position: Int): Boolean {
        // Separators are never collapsed (they're the header)
        if (item.showSeparator) return false

        val groupValue = getGroupValueFor(item, sortOrder)
        if (groupValue.isEmpty()) return false

        val key = "$sortOrder:$groupValue"
        return collapsedGroups.contains(key)
    }

    /**
     * Clear all collapse state when sort order changes.
     */
    private fun clearCollapseState() {
        if (collapsedGroups.isNotEmpty()) {
            collapsedGroups.clear()
            if (BuildConfig.DEBUG) Log.d(TAG, "Cleared collapse state due to sort change")
        }
    }

    /**
     * Get all currently loaded items as a List.
     * Renamed from snapshot() to avoid conflict with PagingDataAdapter.snapshot()
     */
    fun getAllItems(): List<MemberItem> {
        val list = mutableListOf<MemberItem>()
        for (i in 0 until itemCount) {
            getItem(i)?.let { list.add(it) }
        }
        return list
    }

    // ================================================================
    // Force refresh
    // ================================================================

    fun forceRefresh() {
        if (BuildConfig.DEBUG) Log.d(TAG, "🔄 Adapter forceRefresh called - itemCount: $itemCount")
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = listView

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "🔄 onCreateViewHolder called! viewType=$viewType")
        }
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_COMPACT) {
            CompactViewHolder(ListItemBinding.inflate(inflater, parent, false))
        } else {
            DetailedViewHolder(ListItem2Binding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "🔄 onBindViewHolder position=$position, itemCount=$itemCount")
        }
        val item = getItem(position)
        if (item == null) {
            if (BuildConfig.DEBUG) Log.w(TAG, "⚠️ getItem returned null for position $position")
            return
        }
        val hasPending = pendingReminderGuids.contains(item.guid)
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "🔔 Item ${item.name} ${item.surname} GUID=${item.guid} hasPending=$hasPending"
        )
        holder.bind(item, hasPending, position)
    }

    override fun onViewRecycled(holder: MemberViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(holder.itemView).clear(holder.fotoImageView)
    }

    override fun onViewDetachedFromWindow(holder: MemberViewHolder) {
        super.onViewDetachedFromWindow(holder)
        Glide.with(holder.itemView).clear(holder.fotoImageView)
    }

    fun getItemAt(position: Int): MemberItem? {
        val item = getItem(position)
        if (item == null && position < itemCount) {
            if (BuildConfig.DEBUG) Log.w(
                TAG,
                "⚠️ getItem returned null for position $position (itemCount=$itemCount)"
            )
        }
        return item
    }

    override fun getItemCount(): Int {
        val count = super.getItemCount()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "📊 getItemCount = $count")
        }
        return count
    }

    // -------------------------------------------------------------------------
    // Public methods to update display settings
    // -------------------------------------------------------------------------

    fun updateState(
        listView: Int,
        soekList: Boolean,
        soek: String,
        recordStatus: String,
        sortOrder: String
    ) {
        if (BuildConfig.DEBUG) Log.d(
            TAG,
            "updateState called: sortOrder=$sortOrder, listView=$listView"
        )

        // Clear highlight cache when search changes
        if (this.soek != soek) {
            synchronized(spannableCache) {
                spannableCache.clear()
            }
        }

        // Clear collapse state when sort order changes
        if (this.sortOrder != sortOrder) {
            clearCollapseState()
        }

        this.sortOrder = sortOrder
        this.soekList = soekList
        this.soek = soek
        this.recordStatus = recordStatus

        if (this.listView != listView) {
            this.listView = listView
            if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
        }
    }

    fun updatePendingReminderGuids(guids: Set<String>) {
        if (BuildConfig.DEBUG) Log.d(TAG, "📢 Adapter updating GUIDs: $guids")
        if (pendingReminderGuids != guids) {
            pendingReminderGuids = guids
        }
    }

    fun rebindVisibleItems(recyclerView: RecyclerView) {
        val layoutManager =
            recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                ?: return
        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return
        for (index in first..last) {
            notifyItemChanged(index)
        }
    }

    fun getCurrentItems(): List<MemberItem> {
        val list = mutableListOf<MemberItem>()
        for (i in 0 until itemCount) {
            getItem(i)?.let { list.add(it) }
        }
        return list
    }

    // -------------------------------------------------------------------------
    // ViewHolder hierarchy
    // -------------------------------------------------------------------------

    abstract inner class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract val nameTextView: TextView
        abstract val vanTextView: TextView
        abstract val cellTextView: TextView
        abstract val telTextView: TextView
        abstract val wykTextView: TextView
        abstract val ouderdomTextView: TextView
        abstract val verjaarTextView: TextView
        abstract val huwelikTextView: TextView
        abstract val koekImageView: ImageView
        abstract val eposImageView: ImageView
        abstract val whatsappImageView: ImageView
        abstract val fotoImageView: ImageView
        abstract val selBlock: View
        abstract val telBlock: View
        abstract val fotoFrame: View
        abstract val separatorBlock: View
        abstract val separatorTextView: TextView
        abstract val separatorWykTextView: TextView
        abstract val ringImageView: ImageView
        abstract val listBediening: ImageView
        abstract val contentView: View
        abstract val updownContainer: View
        abstract val updownIcon: ImageView
        abstract val cardView: com.google.android.material.card.MaterialCardView

        private fun getContrastColorForBg(bgColor: Int): Int {
            if (bgColor == Color.TRANSPARENT) {
                val typedValue = android.util.TypedValue()
                itemView.context.theme.resolveAttribute(
                    android.R.attr.textColorPrimary,
                    typedValue,
                    true
                )
                return typedValue.data
            }
            val darkness = 1 - (
                    0.299 * Color.red(bgColor) +
                            0.587 * Color.green(bgColor) +
                            0.114 * Color.blue(bgColor)
                    ) / 255
            return if (darkness >= 0.5) Color.WHITE else Color.BLACK
        }

        fun setItemBackgroundColor(color: Int) {
            contentView.setBackgroundColor(color)
        }

        fun bind(item: MemberItem, hasPending: Boolean, position: Int) {
            val context = itemView.context
            val settings = SettingsManager.getInstance(context)

            // ============================================================
            // COLLAPSE CHECK - Determine if this section is collapsed
            // ============================================================
            val groupValue = getGroupValueFor(item, sortOrder)
            val key = "$sortOrder:$groupValue"
            val isSectionCollapsed = collapsedGroups.contains(key)

            // ============================================================
            // Handle visibility based on collapse state
            // ============================================================
            if (item.showSeparator) {
                // This is a separator row - show the separator, hide the card if collapsed
                // Reset the item view to visible (separator should always be visible)
                itemView.layoutParams = itemView.layoutParams.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                itemView.visibility = View.VISIBLE

                // Debug logging for WYK sort
                if (sortOrder == "WYK") {
                    Log.d(
                        TAG,
                        "WYK separator at position $position: ward=${item.ward}, showSeparator=${item.showSeparator}, showSeparator2=${item.showSeparator2}"
                    )
                }
            } else {
                // This is a regular item - hide it if its section is collapsed
                if (isSectionCollapsed) {
                    itemView.layoutParams = itemView.layoutParams.apply {
                        height = 0
                    }
                    itemView.visibility = View.GONE
                    return
                } else {
                    itemView.layoutParams = itemView.layoutParams.apply {
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    itemView.visibility = View.VISIBLE
                }
            }

            // ------------------------------------------------------------
            // BACKGROUND
            // ------------------------------------------------------------
            var finalBgColor = Color.TRANSPARENT

            if (soekList) {
                finalBgColor = Color.LTGRAY
                setItemBackgroundColor(finalBgColor)
            } else {
                val congregationColor = when (item.congregation) {
                    settings.gemeenteNaam -> settings.gemeenteKleur
                    settings.gemeente2Naam -> settings.gemeente2Kleur
                    settings.gemeente3Naam -> settings.gemeente3Kleur
                    else -> Int.MIN_VALUE
                }
                finalBgColor =
                    if (congregationColor != Int.MIN_VALUE) congregationColor else Color.TRANSPARENT
                setItemBackgroundColor(finalBgColor)

                when {
                    item.tag == 1 -> {
                        finalBgColor = ContextCompat.getColor(context, R.color.selected_view)
                        setItemBackgroundColor(finalBgColor)
                    }
                    item.recordstatus == "2" -> {
                        val inactiveColor = settings.inactiveBackgroundColor
                        finalBgColor =
                            if (inactiveColor != Int.MIN_VALUE) inactiveColor else Color.TRANSPARENT
                        setItemBackgroundColor(finalBgColor)
                    }
                }
            }

            // ------------------------------------------------------------
            // TEXT COLOR
            // ------------------------------------------------------------
            val textColor = getContrastColorForBg(finalBgColor)
            nameTextView.setTextColor(textColor)
            vanTextView.setTextColor(textColor)
            cellTextView.setTextColor(textColor)
            telTextView.setTextColor(textColor)
            wykTextView.setTextColor(textColor)
            ouderdomTextView.setTextColor(textColor)
            verjaarTextView.setTextColor(textColor)
            huwelikTextView.setTextColor(textColor)

            applyVisibilitySettings(settings)
            resetViewState()

            bindPhotoData(item, itemView)
            bindBasicInfo(item)
            bindContactInfo(item, settings)
            bindAgeInfo(item, settings)
            bindWeddingInfo(item, settings)
            bindEmailIndicator(item, settings)

            // ------------------------------------------------------------
            // SEARCH HIGHLIGHTING with caching
            // ------------------------------------------------------------
            if (soekList && soek.isNotEmpty()) {
                val searchTerm = soek
                val originalVan = item.surname
                val originalName = item.name
                val originalCell = if (item.cellphone.isNotEmpty()) fixphonenumber(item.cellphone)
                    ?: item.cellphone else ""
                val originalTel = if (item.landline.isNotEmpty()) fixphonenumber(item.landline)
                    ?: item.landline else ""

                vanTextView.text = highlight(searchTerm, originalVan)
                nameTextView.text = highlight(searchTerm, originalName)
                cellTextView.text =
                    if (originalCell.isNotEmpty()) highlight(searchTerm, originalCell) else ""
                telTextView.text =
                    if (originalTel.isNotEmpty()) highlight(searchTerm, originalTel) else ""
            } else {
                vanTextView.text = item.surname
                nameTextView.text = item.name
                cellTextView.text = if (item.cellphone.isNotEmpty()) fixphonenumber(item.cellphone)
                    ?: item.cellphone else ""
                telTextView.text = if (item.landline.isNotEmpty()) fixphonenumber(item.landline)
                    ?: item.landline else ""
            }

            bindSeparator(item)

            // ------------------------------------------------------------
            // CARD VISIBILITY - Hide card content when section is collapsed
            // ------------------------------------------------------------
            if (item.showSeparator && isSectionCollapsed) {
                contentView.visibility = View.GONE
                cardView.visibility = View.GONE
            } else {
                contentView.visibility = View.VISIBLE
                cardView.visibility = View.VISIBLE
            }

            // ------------------------------------------------------------
            // ADDRESS MAP CLICK
            // ------------------------------------------------------------
            val isAddressSort = sortOrder == "ADRES" || sortOrder == "GESINNE" || sortOrder == "WYK"
            if (isAddressSort && (item.showSeparator || item.showSeparator2) && item.address.isNotEmpty()) {
                separatorBlock.setOnClickListener { view ->
                    try {
                        val encodedAddress = Uri.encode(item.address)
                        val uri = Uri.parse("geo:0,0?q=$encodedAddress")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        view.context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://maps.google.com/maps?q=" + Uri.encode(item.address))
                            )
                            view.context.startActivity(intent)
                        } catch (e2: Exception) {
                            Toast.makeText(
                                view.context,
                                "Geen kaarttoepassing gevind",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } else {
                separatorBlock.setOnClickListener(null)
            }

            // ============================================================
            // COLLAPSE BUTTON - ALWAYS show on separator rows
            // regardless of collapse state
            // ============================================================
            if (item.showSeparator) {
                // The button should ALWAYS be visible on separator rows
                updownContainer.visibility = View.VISIBLE

                // Set the icon based on collapse state
                val isGroupCollapsed = collapsedGroups.contains(key)
                updownIcon.setImageResource(
                    if (isGroupCollapsed) R.drawable.ic_chevron_down else R.drawable.ic_chevron_up
                )
                // Force tint to be visible
                updownIcon.setColorFilter(
                    ContextCompat.getColor(context, R.color.text_secondary_light),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )

                updownContainer.setOnClickListener {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Toggle collapse for group: $key at position $position")
                    }
                    toggleGroupCollapsed(sortOrder, groupValue, position)
                }
            } else {
                updownContainer.visibility = View.GONE
                updownContainer.setOnClickListener(null)
            }

            itemView.setOnClickListener { onItemClick(it, item, bindingAdapterPosition) }
            itemView.setOnLongClickListener { onItemLongClick(item, bindingAdapterPosition) }

            listBediening.visibility = if (hasPending) View.VISIBLE else View.GONE
        }

        // -------- Helper methods --------

        private fun applyVisibilitySettings(settings: SettingsManager) {
            fotoFrame.visibility = if (settings.isListFoto) View.VISIBLE else View.GONE
            ouderdomTextView.visibility = if (settings.isListOuderdom) View.VISIBLE else View.GONE
            wykTextView.visibility = if (settings.isListWyk) View.VISIBLE else View.GONE
            huwelikTextView.visibility = if (settings.isListHuwelikBlok) View.VISIBLE else View.GONE
            eposImageView.visibility = if (settings.isListEpos) View.VISIBLE else View.GONE
            if (settings.isListVerjaarBlok) {
                koekImageView.visibility = View.VISIBLE
                verjaarTextView.visibility = View.VISIBLE
            } else {
                koekImageView.visibility = View.GONE
                verjaarTextView.visibility = View.GONE
            }
        }

        private fun resetViewState() {
            koekImageView.visibility = View.GONE
            selBlock.visibility = View.GONE
            telBlock.visibility = View.GONE
            whatsappImageView.visibility = View.GONE
            huwelikTextView.text = ""
            ringImageView.visibility = View.GONE
            eposImageView.visibility = View.GONE
            wykTextView.visibility = View.GONE
        }

        private fun bindPhotoData(item: MemberItem, view: View) {
            val density = view.context.resources.displayMetrics.density
            val sizeDp = if (listView == VIEW_TYPE_DETAILED) 50 else 30
            val pixels = (sizeDp * density + 0.5f).toInt()
            fotoImageView.layoutParams.width = pixels
            fotoImageView.layoutParams.height = pixels
            fotoImageView.requestLayout()

            val defaultDrawable = when (item.gender) {
                "Manlik" -> ContextCompat.getDrawable(view.context, R.drawable.kman)
                else -> ContextCompat.getDrawable(view.context, R.drawable.kvrou)
            } ?: ContextCompat.getDrawable(view.context, R.drawable.kontak)

            val photoFile = PhotoHelper.getSyncedPhotoFile(view.context, item.guid)

            // Only try to load if the file exists
            if (photoFile != null && photoFile.exists()) {
                Glide.with(view)
                    .load(photoFile)
                    .apply(PHOTO_OPTIONS)
                    .placeholder(defaultDrawable)
                    .error(defaultDrawable)
                    .override(pixels, pixels)
                    .into(fotoImageView)
            } else {
                // Use default drawable immediately
                fotoImageView.setImageDrawable(defaultDrawable)
            }
        }

        private fun bindBasicInfo(item: MemberItem) {
            nameTextView.text = item.name
            vanTextView.text = item.surname
        }

        private fun bindContactInfo(item: MemberItem, settings: SettingsManager) {
            if (item.cellphone.isNotEmpty()) {
                val formattedCell = fixphonenumber(item.cellphone) ?: item.cellphone
                if (settings.isListSelfoon) {
                    selBlock.visibility = View.VISIBLE
                    cellTextView.text = formattedCell
                } else {
                    selBlock.visibility = View.GONE
                }
                if (settings.isListWhatsapp) {
                    if (ContactRepository.isWhatsAppContact(formattedCell)) {
                        whatsappImageView.visibility = View.VISIBLE
                    }
                }
            } else {
                cellTextView.text = ""
            }

            val validSortOrder = sortOrder in listOf("VAN", "OUDERDOM", "VERJAAR", "HUWELIK")
            if (item.ward.isNotEmpty() && settings.isListWyk && validSortOrder) {
                wykTextView.visibility = View.VISIBLE
                wykTextView.text = item.ward
            } else {
                wykTextView.text = ""
            }

            if (item.landline.isNotEmpty()) {
                val formattedLandline = fixphonenumber(item.landline) ?: item.landline
                telBlock.visibility = if (settings.isListTelefoon) View.VISIBLE else View.GONE
                telTextView.text = formattedLandline
            } else {
                telTextView.text = ""
            }
        }

        private fun bindAgeInfo(item: MemberItem, settings: SettingsManager) {
            if (item.birthday.isNotEmpty() && settings.isListOuderdom && item.birthday.length >= 10) {
                ouderdomTextView.text = "(${item.age})"
                ouderdomTextView.visibility = View.VISIBLE
                val day = item.birthday.substring(0, 2)
                val month = item.birthday.substring(3, 5)
                verjaarTextView.text = "$day ${getMonthAbbreviation(month)}"

                val today = java.time.LocalDate.now()
                if (item.birthday.length >= 5) {
                    val bMonth = item.birthday.substring(3, 5).trimStart('0').toIntOrNull() ?: 0
                    val bDay = item.birthday.substring(0, 2).trimStart('0').toIntOrNull() ?: 0
                    if (bMonth == today.monthValue && bDay == today.dayOfMonth && settings.isListVerjaarBlok) {
                        koekImageView.visibility = View.VISIBLE
                    } else {
                        koekImageView.visibility = View.GONE
                    }
                }
            } else {
                ouderdomTextView.text = ""
            }
        }

        private fun bindWeddingInfo(item: MemberItem, settings: SettingsManager) {
            huwelikTextView.visibility = View.GONE
            if (item.weddingDate.isNotEmpty() && settings.isListHuwelikBlok && item.weddingDate.length > 6) {
                ringImageView.visibility = View.VISIBLE
                val day = item.weddingDate.substring(0, 2)
                val month = item.weddingDate.substring(3, 5)
                huwelikTextView.text = "$day ${getMonthAbbreviation(month)} (${item.weddingYears})"
                huwelikTextView.visibility = View.VISIBLE
            }
        }

        private fun bindEmailIndicator(item: MemberItem, settings: SettingsManager) {
            eposImageView.visibility =
                if (item.email.isNotEmpty() && settings.isListEpos) View.VISIBLE else View.GONE
        }

        private fun bindSeparator(item: MemberItem) {
            val hasSeparator = item.showSeparator || item.showSeparator2
            val hasText =
                !item.separatorLabel.isNullOrBlank() || !item.separatorWykLabel.isNullOrBlank()

            if (hasSeparator && hasText) {
                separatorTextView.text = item.separatorLabel
                separatorWykTextView.text = item.separatorWykLabel

                // For WYK view, we show "Wyk: [ward]" in the left text
                // Hide the right text completely in WYK view
                if (sortOrder == "WYK") {
                    separatorWykTextView.visibility = View.GONE
                } else {
                    separatorWykTextView.visibility = View.VISIBLE
                }

                separatorBlock.visibility = View.VISIBLE

                // Only set address click for ADRES and GESINNE sorts (not WYK)
                val isAddressSort = sortOrder == "ADRES" || sortOrder == "GESINNE"
                if (isAddressSort && item.address.isNotEmpty()) {
                    separatorTextView.setOnClickListener { view ->
                        openMaps(view, item.address)
                    }
                    separatorWykTextView.setOnClickListener { view ->
                        openMaps(view, item.address)
                    }
                } else {
                    separatorTextView.setOnClickListener(null)
                    separatorWykTextView.setOnClickListener(null)
                }
            } else {
                separatorBlock.visibility = View.GONE
                separatorTextView.setOnClickListener(null)
                separatorWykTextView.setOnClickListener(null)
            }
        }

        private fun openMaps(view: View, address: String) {
            try {
                val encodedAddress = Uri.encode(address)
                val uri = Uri.parse("geo:0,0?q=$encodedAddress")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                view.context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://maps.google.com/maps?q=" + Uri.encode(address))
                    )
                    view.context.startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(
                        view.context,
                        "Geen kaarttoepassing gevind",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // -------- Utility functions --------

        private fun highlight(search: String, originalText: String): CharSequence {
            if (search.isEmpty() || originalText.isEmpty()) return originalText

            val cacheKey = "$search|$originalText"
            synchronized(spannableCache) {
                spannableCache[cacheKey]?.let { return it }
            }

            val searchLower = search.lowercase(Locale.ROOT)
            val originalLower = originalText.lowercase(Locale.ROOT)

            if (!originalLower.contains(searchLower)) return originalText

            val highlighted = SpannableString(originalText)
            var startIndex = 0

            while (startIndex < originalLower.length) {
                val foundIndex = originalLower.indexOf(searchLower, startIndex)
                if (foundIndex == -1) break

                val endIndex = (foundIndex + search.length).coerceAtMost(originalText.length)
                highlighted.setSpan(
                    BackgroundColorSpan(Color.YELLOW),
                    foundIndex,
                    endIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = endIndex
            }

            synchronized(spannableCache) {
                spannableCache[cacheKey] = highlighted
            }

            return highlighted
        }

        private fun getMonthAbbreviation(month: String): String = when (month) {
            "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mrt"; "04" -> "Apr"
            "05" -> "Mei"; "06" -> "Jun"; "07" -> "Jul"; "08" -> "Aug"
            "09" -> "Sept"; "10" -> "Okt"; "11" -> "Nov"; "12" -> "Des"
            else -> ""
        }
    }

    // -------- ViewHolder implementations --------

    private inner class CompactViewHolder(val binding: ListItemBinding) :
        MemberViewHolder(binding.root) {
        override val nameTextView: TextView = binding.listName
        override val vanTextView: TextView = binding.listVan
        override val cellTextView: TextView = binding.listCellnumber
        override val telTextView: TextView = binding.listLandlyn
        override val wykTextView: TextView = binding.listWyk
        override val ouderdomTextView: TextView = binding.listOuderdom
        override val verjaarTextView: TextView = binding.listVerjaar
        override val huwelikTextView: TextView = binding.listHuwelik
        override val koekImageView: ImageView = binding.listBday
        override val eposImageView: ImageView = binding.listEpos
        override val whatsappImageView: ImageView = binding.listWhatsapp
        override val fotoImageView: ImageView = binding.listKontakFoto
        override val selBlock: View = binding.listCellBlock
        override val telBlock: View = binding.listTelBlock
        override val fotoFrame: View = binding.kontakFrame
        override val separatorBlock: View = binding.listSeperatorBlok
        override val separatorTextView: TextView = binding.listSeparator
        override val separatorWykTextView: TextView = binding.listSeparatorwyk
        override val ringImageView: ImageView = binding.listRing
        override val listBediening: ImageView = binding.listBediening
        override val contentView: View = binding.itemContent
        override val updownContainer: View = binding.updownContainer
        override val updownIcon: ImageView = binding.updown
        override val cardView: com.google.android.material.card.MaterialCardView =
            binding.listCardView
    }

    private inner class DetailedViewHolder(val binding: ListItem2Binding) :
        MemberViewHolder(binding.root) {
        override val nameTextView: TextView = binding.listName
        override val vanTextView: TextView = binding.listVan
        override val cellTextView: TextView = binding.listCellnumber
        override val telTextView: TextView = binding.listLandlyn
        override val wykTextView: TextView = binding.listWyk
        override val ouderdomTextView: TextView = binding.listOuderdom
        override val verjaarTextView: TextView = binding.listVerjaar
        override val huwelikTextView: TextView = binding.listHuwelik
        override val koekImageView: ImageView = binding.listBday
        override val eposImageView: ImageView = binding.listEpos
        override val whatsappImageView: ImageView = binding.listWhatsapp
        override val fotoImageView: ImageView = binding.listKontakFoto
        override val selBlock: View = binding.listCellBlock
        override val telBlock: View = binding.listTelBlock
        override val fotoFrame: View = binding.kontakFrame
        override val separatorBlock: View = binding.listSeperatorBlok
        override val separatorTextView: TextView = binding.listSeparator
        override val separatorWykTextView: TextView = binding.listSeparatorwyk
        override val ringImageView: ImageView = binding.listRing
        override val listBediening: ImageView = binding.listBediening
        override val contentView: View = binding.itemContent
        override val updownContainer: View = binding.updownContainer
        override val updownIcon: ImageView = binding.updown
        override val cardView: com.google.android.material.card.MaterialCardView =
            binding.listCardView
    }
}