package za.co.jpsoft.winkerkreader.ui.controllers

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.BuildConfig
import za.co.jpsoft.winkerkreader.data.models.FilterBox
import za.co.jpsoft.winkerkreader.ui.components.SearchCheckBox

/**
 * Owns and registers all [ActivityResultLauncher] instances for [za.co.jpsoft.winkerkreader.ui.activities.MainActivity].
 *
 * Must be created at field-initialisation time (before [AppCompatActivity.onCreate]),
 * because [registerForActivityResult] must be called before the Activity reaches
 * the STARTED state.  Pass callbacks as constructor lambdas so the coordinator
 * stays decoupled from the Activity's internal state.
 *
 * @param activity              the host Activity (used for registration only)
 * @param searchCheckBoxKey     Intent extra key for search checkbox lists
 * @param filterBoxKey          Intent extra key for filter box lists
 * @param onSearchResult        invoked with the returned [SearchCheckBox] list on OK
 * @param onFilterResult        invoked with the returned [FilterBox] list + current sort-order on OK
 * @param onCancelled           invoked when either search or filter result is cancelled
 */
class ActivityResultCoordinator(
    activity: AppCompatActivity,
    private val searchCheckBoxKey: String,
    private val filterBoxKey: String,
    private val onSearchResult: (ArrayList<SearchCheckBox>) -> Unit,
    private val onFilterResult: (ArrayList<FilterBox>) -> Unit,
    private val onCancelled: () -> Unit
) {
    private val TAG = "ActivityResultCoord"

    /** Launcher for the Search configuration screen. */
    val searchLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                result.data?.let { data ->
                    val list: ArrayList<SearchCheckBox>? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            data.getParcelableArrayListExtra(
                                searchCheckBoxKey,
                                SearchCheckBox::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            data.getParcelableArrayListExtra(searchCheckBoxKey)
                        }
                    if (list != null) onSearchResult(list)
                }
            } else {
                onCancelled()
            }
        }

    /** Launcher for the Filter configuration screen. */
    val filterLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                result.data?.let { data ->
                    val list: ArrayList<FilterBox>? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            data.getParcelableArrayListExtra(filterBoxKey, FilterBox::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            data.getParcelableArrayListExtra(filterBoxKey)
                        }
                    if (list != null) onFilterResult(list)
                }
            } else {
                onCancelled()
            }
        }

    /** Launcher for the system overlay permission screen. */
    val overlayPermissionLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(activity)) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Overlay permission granted")
                }
            }
        }
}
