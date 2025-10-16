package com.infinity.roometric

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.infinity.roometric.data.RoomEntity
import com.infinity.roometric.databinding.ItemRoomBinding
import java.text.SimpleDateFormat
import java.util.*

class RoomAdapter(
    private val onRoomClick: (RoomEntity) -> Unit,
    private val onEditClick: (RoomEntity) -> Unit,
    private val onDeleteClick: (RoomEntity) -> Unit
) : ListAdapter<RoomEntity, RoomAdapter.RoomViewHolder>(RoomDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val binding = ItemRoomBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RoomViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class RoomViewHolder(
        private val binding: ItemRoomBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(room: RoomEntity) {
            binding.apply {
                tvRoomName.text = room.name
                tvRoomDescription.text = if (room.description.isNotEmpty()) {
                    room.description
                } else {
                    "No description"
                }
                
                // Format timestamp
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                tvRoomDate.text = dateFormat.format(Date(room.timestamp))
                
                root.setOnClickListener {
                    onRoomClick(room)
                }
                
                btnEditRoom.setOnClickListener {
                    onEditClick(room)
                }
                
                btnDeleteRoom.setOnClickListener {
                    onDeleteClick(room)
                }
            }
        }
    }
    
    private class RoomDiffCallback : DiffUtil.ItemCallback<RoomEntity>() {
        override fun areItemsTheSame(oldItem: RoomEntity, newItem: RoomEntity): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: RoomEntity, newItem: RoomEntity): Boolean {
            return oldItem == newItem
        }
    }
}
