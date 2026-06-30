package com.smsdemon.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smsdemon.R
import com.smsdemon.databinding.ItemLogBinding
import com.smsdemon.model.SmsLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for the SMS send log.
 *
 * Uses [ListAdapter] + [DiffUtil] so only changed rows are rebound,
 * which prevents unnecessary flicker when the service inserts new rows
 * while the screen is visible.
 */
class LogAdapter : ListAdapter<SmsLog, LogAdapter.LogViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LogViewHolder(private val binding: ItemLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(log: SmsLog) {
            val ctx = binding.root.context

            binding.tvCounter.text   = ctx.getString(R.string.log_counter, log.counter)
            binding.tvPhone.text     = log.phoneNumber
            binding.tvMessage.text   = log.message
            binding.tvRandom.text    = ctx.getString(R.string.log_random, log.randomValue)
            binding.tvTimestamp.text = dateFormat.format(Date(log.timestamp))

            if (log.success) {
                binding.tvStatus.text = ctx.getString(R.string.log_success)
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(ctx, R.color.status_running)
                )
            } else {
                binding.tvStatus.text = ctx.getString(R.string.log_failure, log.errorMsg)
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(ctx, R.color.status_stopped)
                )
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<SmsLog>() {
        override fun areItemsTheSame(oldItem: SmsLog, newItem: SmsLog) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SmsLog, newItem: SmsLog) =
            oldItem == newItem
    }
}
