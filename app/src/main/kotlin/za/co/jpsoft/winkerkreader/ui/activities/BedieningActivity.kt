package za.co.jpsoft.winkerkreader.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import za.co.jpsoft.winkerkreader.R
import za.co.jpsoft.winkerkreader.databinding.ActivityBedieningBinding
import za.co.jpsoft.winkerkreader.receivers.PastoralReminderActionReceiver
import za.co.jpsoft.winkerkreader.ui.adapters.BedieningPagerAdapter
import za.co.jpsoft.winkerkreader.ui.viewmodels.BedieningViewModel
import za.co.jpsoft.winkerkreader.ui.viewmodels.BedieningViewModelFactory
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.snackbar.Snackbar
import za.co.jpsoft.winkerkreader.data.pastoral.repository.PastoralReminderRepository
import za.co.jpsoft.winkerkreader.utils.PastoralDatabaseBackup

class BedieningActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBedieningBinding

    private val viewModel: BedieningViewModel by viewModels {
        BedieningViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBedieningBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lifecycleScope.launch {
            PastoralReminderRepository.create(this@BedieningActivity).ensureSystemTemplates()
        }
        setSupportActionBar(binding.bedieningToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        setupViewPager()
        setupTabBadge()
        handleDeepLink(intent)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupViewPager() {
        val pagerAdapter = BedieningPagerAdapter(this)
        binding.bedieningViewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.bedieningTabLayout, binding.bedieningViewPager) { tab, position ->
            tab.text = pagerAdapter.tabTitle(position)
        }.attach()
    }

    private fun setupTabBadge() {
        lifecycleScope.launch {
            viewModel.tabBadgeCount.collect { count ->
                val tab = binding.bedieningTabLayout.getTabAt(0) ?: return@collect
                if (count > 0) {
                    tab.orCreateBadge.number = count
                } else {
                    tab.removeBadge()
                }
            }
        }
    }

    /** Handles opening from notification — scrolls to the relevant reminder. */
    private fun handleDeepLink(intent: Intent?) {
        val reminderId = intent
            ?.getStringExtra(PastoralReminderActionReceiver.EXTRA_REMINDER_ID)
            ?: return

        // Navigate to Vandag tab (index 0) and request scroll
        binding.bedieningViewPager.currentItem = 0
        viewModel.requestScrollTo(reminderId)
    }

    // 1. Inflate menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bediening, menu)
        return true
    }

    // 2. Handle tap
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_deel_rugsteun -> {
                sharePastoralDb()
                true
            }
            R.id.action_bestuur_sjablone -> {
                TemplateManagerActivity.launch(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // 3. Share implementation
    private fun sharePastoralDb() {
        lifecycleScope.launch {
            // Ensure backup is current before sharing
            PastoralDatabaseBackup.backupNow(applicationContext)

            val backupFile = PastoralDatabaseBackup.findBackupFile(applicationContext)
            if (backupFile == null) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.pastoral_rugsteun_nie_gevind),
                    Snackbar.LENGTH_SHORT
                ).show()
                return@launch
            }

            // Share via FileProvider
            val uri = androidx.core.content.FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.fileprovider",
                backupFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    getString(R.string.pastoral_rugsteun_deel_onderwerp)
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(
                Intent.createChooser(
                    shareIntent,
                    getString(R.string.pastoral_deel_rugsteun)
                )
            )
        }
    }

    companion object {
        /**
         * Launch BedieningActivity, optionally scrolling to a specific reminder.
         */
        fun launch(context: Context, reminderId: String? = null) {
            val intent = Intent(context, BedieningActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (reminderId != null) {
                    putExtra(PastoralReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
                }
            }
            context.startActivity(intent)
        }
    }
}