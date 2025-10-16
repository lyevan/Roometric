package com.infinity.roometric.views

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.infinity.roometric.data.Measurement
import com.infinity.roometric.databinding.ItemMeasurementBinding
import com.infinity.roometric.databinding.ItemMeasurementHeaderBinding

sealed class MeasurementItem {
    data class Header(val title: String) : MeasurementItem()
    data class Data(val measurement: Measurement) : MeasurementItem()
}

class MeasurementAdapter(
    private val onEditClick: (Measurement) -> Unit,
    private val onDeleteClick: (Measurement) -> Unit
) : ListAdapter<MeasurementItem, RecyclerView.ViewHolder>(MeasurementDiffCallback()) {
    
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_DATA = 1
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is MeasurementItem.Header -> VIEW_TYPE_HEADER
            is MeasurementItem.Data -> VIEW_TYPE_DATA
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemMeasurementHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemMeasurementBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                DataViewHolder(binding, onEditClick, onDeleteClick)
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is MeasurementItem.Header -> (holder as HeaderViewHolder).bind(item.title)
            is MeasurementItem.Data -> (holder as DataViewHolder).bind(item.measurement)
        }
    }
    
    class HeaderViewHolder(
        private val binding: ItemMeasurementHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(title: String) {
            binding.tvHeaderTitle.text = title
        }
    }
    
    class DataViewHolder(
        private val binding: ItemMeasurementBinding,
        private val onEditClick: (Measurement) -> Unit,
        private val onDeleteClick: (Measurement) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(measurement: Measurement) {
            binding.apply {
                tvMeasurementName.text = measurement.name
                tvMeasurementDescription.text = if (measurement.description.isNotEmpty()) {
                    measurement.description
                } else {
                    "No description"
                }
                
                val heightStr = String.format("%.2f m", measurement.heightMeters)
                val widthStr = String.format("%.2f m", measurement.widthMeters)
                val areaStr = String.format("%.2f m²", measurement.areaMeters)
                
                tvDimensions.text = "$heightStr × $widthStr"
                tvArea.text = areaStr
                
                tvPlaneType.text = measurement.planeType.name
                
                // Set badge color based on plane type
                val badgeColor = when (measurement.planeType) {
                    com.infinity.roometric.data.PlaneType.FLOOR -> android.graphics.Color.parseColor("#4CAF50")
                    com.infinity.roometric.data.PlaneType.WALL -> android.graphics.Color.parseColor("#2196F3")
                }
                tvPlaneType.setBackgroundColor(badgeColor)
                
                btnEditMeasurement.setOnClickListener {
                    onEditClick(measurement)
                }
                
                btnDeleteMeasurement.setOnClickListener {
                    onDeleteClick(measurement)
                }
            }
        }
    }
    
    private class MeasurementDiffCallback : DiffUtil.ItemCallback<MeasurementItem>() {
        override fun areItemsTheSame(oldItem: MeasurementItem, newItem: MeasurementItem): Boolean {
            return when {
                oldItem is MeasurementItem.Header && newItem is MeasurementItem.Header ->
                    oldItem.title == newItem.title
                oldItem is MeasurementItem.Data && newItem is MeasurementItem.Data ->
                    oldItem.measurement.id == newItem.measurement.id
                else -> false
            }
        }
        
        override fun areContentsTheSame(oldItem: MeasurementItem, newItem: MeasurementItem): Boolean {
            return oldItem == newItem
        }
    }
}
