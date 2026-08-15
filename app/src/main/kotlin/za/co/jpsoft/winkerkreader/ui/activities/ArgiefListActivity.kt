package za.co.jpsoft.winkerkreader.ui.activities

import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.ArgiefBinding
import za.co.jpsoft.winkerkreader.databinding.ArgiefItemBinding
import za.co.jpsoft.winkerkreader.ui.viewmodels.ArgiefViewModel
import za.co.jpsoft.winkerkreader.utils.db.getStringOrEmpty
import za.co.jpsoft.winkerkreader.utils.db.getStringOrNull

class ArgiefListActivity : AuthBaseActivity() {

    private lateinit var binding: ArgiefBinding
    private lateinit var adapter: ArgiefRecyclerAdapter
    private lateinit var viewModel: ArgiefViewModel
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var keuse: String = "Van"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ArgiefBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply insets to RecyclerView
        ViewCompat.setOnApplyWindowInsetsListener(binding.argiefRecyclerView) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }

        // Set up RecyclerView
        adapter = ArgiefRecyclerAdapter(this)
        binding.argiefRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.argiefRecyclerView.adapter = adapter

        viewModel = ViewModelProvider(this)[ArgiefViewModel::class.java]

        // Observe cursor changes
        viewModel.archiveCursor.observe(this, Observer { newCursor ->
            adapter.swapCursor(newCursor)
        })

        // Observe loading state
        viewModel.isLoading.observe(this) { loading ->
            binding.argiefProgress.visibility = if (loading) View.VISIBLE else View.GONE
        }

        setupSortToggleGroup()
        viewModel.loadArchive(keuse)
    }

    private fun setupSortToggleGroup() {
        // Set initial checked button
        when (keuse) {
            "Van" -> binding.argiefSortVan.isChecked = true
            "Datum" -> binding.argiefSortDatum.isChecked = true
            "Rede" -> binding.argiefSortRede.isChecked = true
            else -> binding.argiefSortVan.isChecked = true
        }

        binding.argiefSortGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                keuse = when (checkedId) {
                    R.id.argief_sort_van -> "Van"
                    R.id.argief_sort_datum -> "Datum"
                    R.id.argief_sort_rede -> "Rede"
                    else -> "Van"
                }
                adapter.setSortKey(keuse)
                viewModel.loadArchive(keuse)
            }
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
        adapter.swapCursor(null) // adapter will close old cursor internally if needed
        super.onDestroy()
    }
}

// ----------------------------------------------------------------------------
// RecyclerView Adapter with Cursor support and separators
// ----------------------------------------------------------------------------

class ArgiefRecyclerAdapter(private val context: Context) :
    RecyclerView.Adapter<ArgiefRecyclerAdapter.ViewHolder>() {

    private var cursor: Cursor? = null
    private var sortKey: String = "Van"

    fun swapCursor(newCursor: Cursor?) {
        val oldCursor = cursor
        if (oldCursor === newCursor) return
        cursor = newCursor
        // Optionally close old cursor if not managed by ViewModel
        oldCursor?.close()
        notifyDataSetChanged()
    }

    fun setSortKey(key: String) {
        sortKey = key
        // We don't reload data here; just update for separator logic
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ArgiefItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cursor = cursor
        if (cursor == null || cursor.isClosed || !cursor.moveToPosition(position)) {
            // If cursor is invalid, clear views
            holder.bind(null, sortKey, position)
            return
        }
        holder.bind(cursor, sortKey, position)
    }

    override fun getItemCount(): Int = cursor?.count ?: 0

    inner class ViewHolder(private val binding: ArgiefItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(cursor: Cursor?, sortKey: String, position: Int) {
            if (cursor == null || cursor.isClosed) {
                // Clear everything
                binding.argiefListSeparator.visibility = View.GONE
                binding.argiefVan.text = ""
                binding.argiefGeboortedatum.text = ""
                binding.argiefName.text = ""
                binding.argiefRede.text = ""
                binding.argiefVertrekdatum.text = ""
                binding.argiefBestemming.text = ""
                return
            }

            // Read values
            val lidNaam = cursor.getStringOrEmpty("Name")
            val lidVan = cursor.getStringOrEmpty("Surname")
            val lidGeboortedatum = cursor.getStringOrEmpty("DateOfBirth")
            val rede = cursor.getStringOrEmpty("Reason")
            val bestemming = cursor.getStringOrEmpty("DepartureTo")
            val vertrekDatum = cursor.getStringOrEmpty("DepartureDate")

            // Set data
            binding.argiefVan.text = lidVan
            binding.argiefGeboortedatum.text = lidGeboortedatum
            binding.argiefName.text = lidNaam
            binding.argiefRede.text = rede
            binding.argiefVertrekdatum.text = vertrekDatum
            binding.argiefBestemming.text = bestemming

            // Separator logic
            val current = when (sortKey) {
                "Van" -> cursor.getStringOrNull("Surname")
                "Rede" -> cursor.getStringOrNull("Reason")
                "Datum" -> cursor.getStringOrNull("DepartureDate")
                else -> null
            }

            if (position == 0 && current != null) {
                binding.argiefListSeparator.visibility = View.VISIBLE
                binding.argiefListSeparator.text =
                    context.getString(R.string.separator_format, sortKey, current)
            } else if (position > 0) {
                cursor.moveToPosition(position - 1)
                val previous = when (sortKey) {
                    "Van" -> cursor.getStringOrNull("Surname")
                    "Rede" -> cursor.getStringOrNull("Reason")
                    "Datum" -> cursor.getStringOrNull("DepartureDate")
                    else -> null
                }
                cursor.moveToPosition(position) // move back

                if (previous != null && current != null && previous != current) {
                    binding.argiefListSeparator.visibility = View.VISIBLE
                    binding.argiefListSeparator.text =
                        context.getString(R.string.separator_format, sortKey, current)
                } else {
                    binding.argiefListSeparator.visibility = View.GONE
                }
            } else {
                binding.argiefListSeparator.visibility = View.GONE
            }
        }
    }
}