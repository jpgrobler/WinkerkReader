package za.co.jpsoft.winkerkreader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import za.co.jpsoft.winkerkreader.data.FilterBox
import za.co.jpsoft.winkerkreader.data.WinkerkContract.winkerkEntry.RECORDSTATUS

/**
 * Created by Pieter Grobler on 06/09/2017.
 */
class MyFilter : AppCompatActivity() {

    private var filterList: ArrayList<FilterBox>? = null

    override fun onBackPressed() {
        finish()
        super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.filterscreen)

        val aktiefB = findViewById<Button>(R.id.filter_aktief)
        val onaktiefB = findViewById<Button>(R.id.filter_onaktief)
        val run = findViewById<Button>(R.id.run_filter2)

        aktiefB.setOnClickListener {
            if (aktiefB.background.constantState != resources.getDrawable(R.drawable.aktief0).constantState) {
                RECORDSTATUS = "0"
                aktiefB.setBackgroundResource(R.drawable.aktief0)
                onaktiefB.setBackgroundResource(R.drawable.onaktief)
            } else {
                aktiefB.setBackgroundResource(R.drawable.aktief)
                onaktiefB.setBackgroundResource(R.drawable.onaktief2)
                RECORDSTATUS = "2"
            }
        }

        onaktiefB.setOnClickListener {
            if (onaktiefB.background.constantState != resources.getDrawable(R.drawable.onaktief2).constantState) {
                RECORDSTATUS = "2"
                onaktiefB.setBackgroundResource(R.drawable.onaktief2)
                aktiefB.setBackgroundResource(R.drawable.aktief)
            } else {
                RECORDSTATUS = "0"
                onaktiefB.setBackgroundResource(R.drawable.onaktief)
                aktiefB.setBackgroundResource(R.drawable.aktief0)
            }
        }

        run.setOnClickListener {
            filterList = arrayListOf(
                FilterBox(
                    "Van",
                    findViewById<EditText>(R.id.filter_van).text.toString().trim(),
                    "",
                    findViewById<Spinner>(R.id.filter_van_opsies).selectedItem.toString(),
                    findViewById<CheckBox>(R.id.filter_van_check).isChecked
                ),
                FilterBox(
                    "Noemnaam",
                    findViewById<EditText>(R.id.filter_noemnaam).text.toString().trim(),
                    "",
                    findViewById<Spinner>(R.id.filter_noemnaam_opsies).selectedItem.toString(),
                    findViewById<CheckBox>(R.id.filter_noemnaam_check).isChecked
                ),
                FilterBox(
                    "Ouderdom",
                    findViewById<EditText>(R.id.filter_ouderdom1).text.toString().trim(),
                    findViewById<EditText>(R.id.filter_ouderdom2).text.toString().trim(),
                    findViewById<Spinner>(R.id.filter_ouderdom_opsies).selectedItem.toString(),
                    findViewById<CheckBox>(R.id.filter_ouderdom_check).isChecked
                ),
                FilterBox(
                    "Wyk",
                    findViewById<EditText>(R.id.filter_wyk).text.toString().trim(),
                    "",
                    findViewById<Spinner>(R.id.filter_wyk_opsies).selectedItem.toString(),
                    findViewById<CheckBox>(R.id.filter_wyk_check).isChecked
                ),
                FilterBox(
                    "Geslag",
                    "",
                    "",
                    findViewById<Spinner>(R.id.filter_geslag_opsies).selectedItem.toString(),
                    findViewById<CheckBox>(R.id.filter_geslag_check).isChecked
                ),
                FilterBox(
                    "Huwelikstatus",
                    "",
                    "",
                    findViewById<Spinner>(R.id.filter_huwelikstatus_opsies).selectedItem.toString(),
                    findViewById<CheckBox>(R.id.filter_huwelikstatus_check).isChecked
                ),
                FilterBox(
                    "Lidmaatskap",
                    "",
                    "",
                    findViewById<Spinner>(R.id.filter_lidmaatsakp_opsies).selectedItem.toString(),
                    findViewById<CheckBox>(R.id.filter_lidmaatsakapstatus_check).isChecked
                ),
                FilterBox(
                    "Selfoon",
                    "",
                    "",
                    "",
                    findViewById<CheckBox>(R.id.filter_selfoon).isChecked
                ),
                FilterBox(
                    "Landlyn",
                    "",
                    "",
                    "",
                    findViewById<CheckBox>(R.id.filter_landlyn).isChecked
                ),
                FilterBox(
                    "E-pos",
                    "",
                    "",
                    "",
                    findViewById<CheckBox>(R.id.filter_epos).isChecked
                ),
                FilterBox(
                    "Gesinshoof",
                    "",
                    "",
                    "",
                    findViewById<CheckBox>(R.id.filter_gesinshoof).isChecked
                )
            )

            setResult(Activity.RESULT_OK, Intent().putExtra(MainActivity2.FILTER_CHECK_BOX, filterList))

            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            currentFocus?.let {
                inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
            }

            finish()
        }

        val close = findViewById<Button>(R.id.cancel_filter2)
        close.setOnClickListener {
            currentFocus?.let {
                val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
            }
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        close.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        currentFocus?.let {
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
        }
        super.finish()
    }
}