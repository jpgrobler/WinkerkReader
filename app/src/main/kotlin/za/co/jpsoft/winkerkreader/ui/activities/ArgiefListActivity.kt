package za.co.jpsoft.winkerkreader.ui.activities

import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.cursoradapter.widget.CursorAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.utils.getStringOrNull
import za.co.jpsoft.winkerkreader.databinding.ArgiefBinding
import za.co.jpsoft.winkerkreader.databinding.ArgiefItemBinding
import za.co.jpsoft.winkerkreader.ui.viewmodels.ArgiefViewModel
import za.co.jpsoft.winkerkreader.utils.getStringOrEmpty

class ArgiefListActivity : AppCompatActivity() {

    private lateinit var binding: ArgiefBinding
    private lateinit var mCursorAdapter: ArgiefLysAdapter
    private lateinit var viewModel: ArgiefViewModel
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var keuse: String = "Van"
    private var isObserving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ArgiefBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mCursorAdapter = ArgiefLysAdapter(this, null)
        mCursorAdapter.keuse = keuse
        binding.argiefList.adapter = mCursorAdapter
        binding.argiefList.isFastScrollEnabled = true
        binding.argiefList.isClickable = true

        viewModel = ViewModelProvider(this)[ArgiefViewModel::class.java]

        // FIX: Attach observer BEFORE loading data
        viewModel.archiveCursor.observe(this, Observer { newCursor ->
            if (!isObserving) {
                isObserving = true
                // Swap cursor but DON'T close the old one here
                // The ViewModel will handle closing after a delay
                mCursorAdapter.swapCursor(newCursor)
                isObserving = false
            }
        })

        setupSortRadioGroup()

        // FIX: Load data AFTER observer is attached
        viewModel.loadArchive(keuse)
    }

    private fun setupSortRadioGroup() {
        when (keuse) {
            "Van" -> binding.argiefSortVan.isChecked = true
            "Rede" -> binding.argiefSortRede.isChecked = true
            "Datum" -> binding.argiefSortDatum.isChecked = true
            else -> {
                binding.argiefSortVan.isChecked = true
                keuse = "Van"
            }
        }

        binding.argiefSort.setOnCheckedChangeListener { _, checkedId ->
            keuse = when (checkedId) {
                R.id.argief_sort_van -> "Van"
                R.id.argief_sort_datum -> "Datum"
                R.id.argief_sort_rede -> "Rede"
                else -> "Van"
            }
            mCursorAdapter.keuse = keuse
            viewModel.loadArchive(keuse)
        }
    }

    private fun performArchiveSearch(query: String) {
        if (query.isBlank()) {
            viewModel.loadArchive(keuse)
        } else {
            viewModel.loadArchive(keuse, query)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.argiefmenu, menu)

        val searchItem = menu.findItem(R.id.argief_action_search)
        searchItem.setShowAsActionFlags(
            MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW
        )

        val searchView = searchItem.actionView as SearchView
        searchView.apply {
            setSubmitButtonEnabled(false)
            queryHint = "Soek"

            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        searchRunnable?.let { searchHandler.removeCallbacks(it) }
                        performArchiveSearch(query)
                        return true
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        searchRunnable?.let { searchHandler.removeCallbacks(it) }
                        searchRunnable = Runnable { performArchiveSearch(newText) }
                        searchHandler.postDelayed(searchRunnable!!, 300)
                        return true
                    }
                }
            )
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        super.onStop()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        searchRunnable = null
    }

    override fun onDestroy() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        mCursorAdapter.swapCursor(null)?.close()
        super.onDestroy()
    }
}

// ----------------------------------------------------------------------------
// Adapter with null-safety
// ----------------------------------------------------------------------------

class ArgiefLysAdapter(context: Context, cursor: Cursor?) : CursorAdapter(context, cursor, 0) {

    var keuse: String = "Van"

    class ViewHolder2(val binding: ArgiefItemBinding)

    override fun swapCursor(newCursor: Cursor?): Cursor? {
        // Don't swap if it's the same cursor object
        val oldCursor = cursor
        if (oldCursor === newCursor) {
            return oldCursor
        }
        return super.swapCursor(newCursor)
    }

    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(context)
        val binding = ArgiefItemBinding.inflate(inflater, parent, false)
        val viewHolder = ViewHolder2(binding)
        binding.root.tag = viewHolder
        return binding.root
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        // Safety check - ensure cursor is valid
        if (cursor.isClosed) {
            android.util.Log.e("ArgiefLysAdapter", "Attempted to bind with closed cursor")
            return
        }

        try {
            val viewHolder = view.tag as ViewHolder2
            val itemBinding = viewHolder.binding

            val lidNaam = cursor.getStringOrEmpty("Name")
            val lidVan = cursor.getStringOrEmpty("Surname")
            val lidGeboortedatum = cursor.getStringOrEmpty("DateOfBirth")
            val rede = cursor.getStringOrEmpty("Reason")
            val bestemming = cursor.getStringOrEmpty("DepartureTo")
            val vertrekDatum = cursor.getStringOrEmpty("DepartureDate")

            itemBinding.argiefName.text = lidNaam
            itemBinding.argiefVan.text = lidVan
            itemBinding.argiefGeboortedatum.text = lidGeboortedatum
            itemBinding.argiefRede.text = rede
            itemBinding.argiefBestemming.text = bestemming
            itemBinding.argiefVertrekdatum.text = vertrekDatum
            itemBinding.argiefListSeparator.text = keuse
            itemBinding.argiefListSeparator.visibility = View.GONE

            val position = cursor.position
            val current = when (keuse) {
                "Van" -> cursor.getStringOrNull("Surname")
                "Rede" -> cursor.getStringOrNull("Reason")
                "Datum" -> cursor.getStringOrNull("DepartureDate")
                else -> ""
            }

            if (position == 0) {
                itemBinding.argiefListSeparator.visibility = View.VISIBLE
                itemBinding.argiefListSeparator.text = context.getString(R.string.separator_format, keuse, current)
            } else {
                cursor.moveToPosition(position - 1)
                val previous = when (keuse) {
                    "Van" -> cursor.getStringOrNull("Surname")
                    "Rede" -> cursor.getStringOrNull("Reason")
                    "Datum" -> cursor.getStringOrNull("DepartureDate")
                    else -> ""
                }
                cursor.moveToPosition(position)

                if (previous != null && current != null && previous != current) {
                    itemBinding.argiefListSeparator.visibility = View.VISIBLE
                    itemBinding.argiefListSeparator.text = "${keuse} $current"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ArgiefLysAdapter", "Error binding view: ${e.message}", e)
        }
    }
}