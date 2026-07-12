package za.co.jpsoft.winkerkreader.ui.controllers

import android.app.SearchManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.ui.viewmodels.MemberViewModel
import za.co.jpsoft.winkerkreader.utils.MainNavigationController

class MainMenuController(
    private val activity: FragmentActivity,
    private val tag: String,
    private val viewModel: MemberViewModel,
    private val searchFilterCoordinator: MainSearchFilterCoordinator,
    private val observeDataset: () -> Unit,
    private val navigationController: MainNavigationController,
    private val onSortChanged: (String) -> Unit
) {
    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var optionsMenu: Menu? = null

    fun onCreateOptionsMenu(menu: Menu): Boolean {
        optionsMenu = menu
        if (menu.javaClass.simpleName == "MenuBuilder") {
            try {
                val method =
                    menu.javaClass.getDeclaredMethod("setOptionalIconsVisible", Boolean::class.java)
                method.isAccessible = true
                method.invoke(menu, true)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(tag, "Failed to show menu icons", e)
            }
        }
        activity.menuInflater.inflate(R.menu.menu_main, menu)
        setupSearchView(menu)
        setupStatusFilterControls()
        return true
    }

    fun findSearchView(): SearchView? {
        optionsMenu?.findItem(R.id.action_search)?.let { menuItem ->
            return menuItem.actionView as? SearchView
        }
        return null
    }

    fun clearCallbacks() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        searchRunnable = null
    }

    private fun setupStatusFilterControls() {
        val menuItem = optionsMenu?.findItem(R.id.aktief_radio_group)
        val linearLayout = menuItem?.actionView as? LinearLayout ?: return
        val checkBoxActive = linearLayout.findViewById<CheckBox>(R.id.filter_aktief2)
        val checkBoxInactive = linearLayout.findViewById<CheckBox>(R.id.filter_onaktief2)

        when (viewModel.recordStatus) {
            "0" -> {
                checkBoxActive.isChecked = true
                checkBoxInactive.isChecked = false
            }

            "2" -> {
                checkBoxActive.isChecked = false
                checkBoxInactive.isChecked = true
            }

            "*" -> {
                checkBoxActive.isChecked = true
                checkBoxInactive.isChecked = true
            }

            else -> {
                checkBoxActive.isChecked = true
                checkBoxInactive.isChecked = false
            }
        }

        var isUpdating = false
        val updateFilter = {
            if (!isUpdating) {
                isUpdating = true
                val newStatus =
                    when {
                        checkBoxActive.isChecked && checkBoxInactive.isChecked -> "*"
                        checkBoxActive.isChecked && !checkBoxInactive.isChecked -> "0"
                        !checkBoxActive.isChecked && checkBoxInactive.isChecked -> "2"
                        else -> "0"
                    }

                if (!checkBoxActive.isChecked && !checkBoxInactive.isChecked) {
                    checkBoxActive.isChecked = true
                }

                if (viewModel.recordStatus != newStatus) {
                    viewModel.recordStatus = newStatus
                    if (BuildConfig.DEBUG) Log.d(
                        tag,
                        "Filter changed to: ${viewModel.recordStatus}"
                    )
                    viewModel.clearCache()
                    viewModel.refresh()//observeDataset()
                    // ✅ Also update the row count via the legacy loadData path
                    val mode = searchFilterCoordinator.resolveQueryMode(viewModel.getEventType())
                    viewModel.loadData(mode)
                }
                isUpdating = false
            }
        }

        checkBoxActive.setOnCheckedChangeListener { _, _ -> updateFilter() }
        checkBoxInactive.setOnCheckedChangeListener { _, _ -> updateFilter() }
    }

    private fun setupSearchView(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        searchItem.setShowAsActionFlags(
            MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW
        )
        val searchView = searchItem.actionView as? SearchView ?: return

        searchView.isSubmitButtonEnabled = false
        searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)?.hint = "Soek"
        searchView.findViewById<View>(androidx.appcompat.R.id.search_plate)?.setBackgroundColor(
            ContextCompat.getColor(activity, android.R.color.transparent)
        )

        val searchManager = activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.componentName))

        searchView.setOnCloseListener {
            clearCallbacks()
            searchFilterCoordinator.onSearchClosed()
            activity.invalidateOptionsMenu()
            false
        }

        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    clearCallbacks()
                    searchFilterCoordinator.performSearch(query)
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    clearCallbacks()
                    val runnable = Runnable { searchFilterCoordinator.performSearch(newText) }
                    searchRunnable = runnable
                    searchHandler.postDelayed(runnable, SEARCH_DEBOUNCE_MS)
                    return true
                }
            }
        )
    }


}