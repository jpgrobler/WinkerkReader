package za.co.jpsoft.winkerkreader

import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.cursoradapter.widget.CursorAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import za.co.jpsoft.winkerkreader.data.WinkerkContract
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry

class argief_List : AppCompatActivity() {

    private lateinit var argiefListView: ListView
    private lateinit var mCursorAdapter: argiefLysAdapter
    private lateinit var viewModel: ArgiefViewModel
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.argief)

        argiefListView = findViewById(R.id.argief_list)
        mCursorAdapter = argiefLysAdapter(this, null)
        argiefListView.adapter = mCursorAdapter
        argiefListView.isFastScrollEnabled = true
        argiefListView.isClickable = true

        viewModel = ViewModelProvider(this)[ArgiefViewModel::class.java]
        viewModel.archiveCursor.observe(this, Observer { cursor ->
            mCursorAdapter.swapCursor(cursor)
        })

        setupSortRadioGroup()
        viewModel.loadArchive(winkerkEntry.KEUSE)
    }

    private fun setupSortRadioGroup() {
        val sortvan = findViewById<RadioButton>(R.id.argief_sort_van)
        val sortdatum = findViewById<RadioButton>(R.id.argief_sort_datum)
        val sortrede = findViewById<RadioButton>(R.id.argief_sort_rede)

        when (winkerkEntry.KEUSE) {
            "Van" -> sortvan.isChecked = true
            "Rede" -> sortrede.isChecked = true
            "Datum" -> sortdatum.isChecked = true
            else -> {
                sortvan.isChecked = true
                winkerkEntry.KEUSE = "Van"
            }
        }

        val radioGroup = findViewById<RadioGroup>(R.id.argief_sort)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            winkerkEntry.KEUSE = when (checkedId) {
                R.id.argief_sort_van -> "Van"
                R.id.argief_sort_datum -> "Datum"
                R.id.argief_sort_rede -> "Rede"
                else -> "Van"
            }
            viewModel.loadArchive(winkerkEntry.KEUSE)
        }
    }

    private fun performArchiveSearch(query: String) {
        if (query.isBlank()) {
            viewModel.loadArchive(winkerkEntry.KEUSE)   // load all
        } else {
            viewModel.loadArchive(winkerkEntry.KEUSE, query)
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
            setSubmitButtonEnabled(false)   // ← change to false
            findViewById<EditText>(androidx.appcompat.R.id.search_src_text)?.hint = "Soek"
            findViewById<View>(androidx.appcompat.R.id.search_plate)?.setBackgroundColor(
                ContextCompat.getColor(this@argief_List, android.R.color.transparent)
            )

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    // optional: immediate search on submit
                    searchRunnable?.let { searchHandler.removeCallbacks(it) }
                    performArchiveSearch(query)
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    // debounce
                    searchRunnable?.let { searchHandler.removeCallbacks(it) }
                    searchRunnable = Runnable {
                        performArchiveSearch(newText)
                    }
                    searchHandler.postDelayed(searchRunnable!!, 300)
                    return true
                }
            })
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        mCursorAdapter.swapCursor(null)
        super.onDestroy()
    }
}

// ----------------------------------------------------------------------------
// Adapter (unchanged, but uses cursor extensions)
// ----------------------------------------------------------------------------

class argiefLysAdapter(context: Context, cursor: Cursor?) : CursorAdapter(context, cursor, 0) {

    class ViewHolder(view: View) {
        val GroepNaamView: TextView = view.findViewById(R.id.GroepNaam)
        val GroepCountView: TextView = view.findViewById(R.id.GroepCount)
    }

    class ViewHolder2(view: View) {
        val redeTextView: TextView = view.findViewById(R.id.argief_rede)
        val nameTextView: TextView = view.findViewById(R.id.argief_name)
        val geboortedatumTextView: TextView = view.findViewById(R.id.argief_geboortedatum)
        val vanTextView: TextView = view.findViewById(R.id.argief_van)
        val bestemmingTextView: TextView = view.findViewById(R.id.argief_bestemming)
        val vertrekTextview: TextView = view.findViewById(R.id.argief_vertrekdatum)
        val separatorView: TextView = view.findViewById(R.id.argief_list_separator)
    }

    override fun swapCursor(newCursor: Cursor?): Cursor? = super.swapCursor(newCursor)

    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        if (cursor == null || cursor.count < 1) return LayoutInflater.from(context).inflate(R.layout.argief_item, parent, false)

        val view = LayoutInflater.from(context).inflate(R.layout.argief_item, parent, false)
        val viewHolder = ViewHolder2(view)
        view.tag = viewHolder
        return view
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        if (cursor == null || cursor.count < 1) return

        val viewHolder2 = view.tag as ViewHolder2

        val lidNaam = cursor.getStringOrEmpty("Name")
        val lidVan = cursor.getStringOrEmpty("Surname")
        val lidGeboortedatum = cursor.getStringOrEmpty("DateOfBirth")
        val rede = cursor.getStringOrEmpty("Reason")
        val bestemming = cursor.getStringOrEmpty("DepartureTo")
        val vertrekDatum = cursor.getStringOrEmpty("DepartureDate")

        viewHolder2.nameTextView.text = lidNaam
        viewHolder2.vanTextView.text = lidVan
        viewHolder2.geboortedatumTextView.text = lidGeboortedatum
        viewHolder2.redeTextView.text = rede
        viewHolder2.bestemmingTextView.text = bestemming
        viewHolder2.vertrekTextview.text = vertrekDatum
        viewHolder2.separatorView.text = winkerkEntry.KEUSE
        viewHolder2.separatorView.visibility = View.GONE

        val position = cursor.position
        val current = when (winkerkEntry.KEUSE) {
            "Van" -> cursor.getStringOrNull("Surname")
            "Rede" -> cursor.getStringOrNull("Reason")
            "Datum" -> cursor.getStringOrNull("DepartureDate")
            else -> ""
        }

        if (position == 0) {
            viewHolder2.separatorView.visibility = View.VISIBLE
            viewHolder2.separatorView.text = "${winkerkEntry.KEUSE} $current"
        } else {
            cursor.moveToPosition(position - 1)
            val previous = when (winkerkEntry.KEUSE) {
                "Van" -> cursor.getStringOrNull("Surname")
                "Rede" -> cursor.getStringOrNull("Reason")
                "Datum" -> cursor.getStringOrNull("DepartureDate")
                else -> ""
            }
            cursor.moveToPosition(position)

            if (previous != null && current != null && previous != current) {
                viewHolder2.separatorView.visibility = View.VISIBLE
                viewHolder2.separatorView.text = "${winkerkEntry.KEUSE} $current"
            }
        }
    }
}