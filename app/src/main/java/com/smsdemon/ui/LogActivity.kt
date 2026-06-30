package com.smsdemon.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.smsdemon.R
import com.smsdemon.databinding.ActivityLogBinding

/**
 * Displays a scrollable list of all SMS send attempts, newest first.
 *
 * Backed by [LogViewModel] which exposes a LiveData<List<SmsLog>> sourced
 * from Room via a Flow.  The list updates automatically when the service
 * inserts new rows while this screen is visible.
 */
class LogActivity : AppCompatActivity() {

    private lateinit var binding:  ActivityLogBinding
    private val viewModel: LogViewModel by viewModels()
    private lateinit var adapter:  LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show back arrow in toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_log_screen)

        setupRecyclerView()
        observeViewModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_clear_logs -> { confirmClear(); true }
        else -> super.onOptionsItemSelected(item)
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = LogAdapter()
        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.logs.observe(this) { logs ->
            adapter.submitList(logs)
            binding.tvEmptyState.visibility =
                if (logs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_clear_title)
            .setMessage(R.string.dialog_clear_message)
            .setPositiveButton(R.string.action_clear) { _, _ -> viewModel.clearLogs() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
