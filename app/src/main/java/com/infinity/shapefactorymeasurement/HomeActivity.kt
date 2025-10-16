package com.infinity.roometric

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infinity.roometric.data.RoomEntity
import com.infinity.roometric.databinding.ActivityHomeBinding
import com.infinity.roometric.viewmodel.RoomViewModel
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private val viewModel: RoomViewModel by viewModels()
    private lateinit var roomAdapter: RoomAdapter
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Storage permission is needed to save measurements", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        checkAndRequestStoragePermission()
        setupRecyclerView()
        setupClickListeners()
        observeRooms()
    }
    
    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ doesn't need WRITE_EXTERNAL_STORAGE
                return
            } else {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
            
            if (ContextCompat.checkSelfPermission(this, permission) 
                != PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(permission)
            }
        }
    }
    
    private fun setupRecyclerView() {
        roomAdapter = RoomAdapter(
            onRoomClick = { room ->
                // Navigate to AR screen with room ID
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("ROOM_ID", room.id)
                    putExtra("ROOM_NAME", room.name)
                }
                startActivity(intent)
            },
            onEditClick = { room -> showEditRoomDialog(room) },
            onDeleteClick = { room -> showDeleteRoomDialog(room) }
        )
        
        binding.recyclerViewRooms.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = roomAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.fabNewRoom.setOnClickListener {
            showCreateRoomDialog()
        }
    }
    
    private fun observeRooms() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allRooms.collect { rooms ->
                    roomAdapter.submitList(rooms)
                    binding.tvEmptyState.visibility = if (rooms.isEmpty()) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
                }
            }
        }
    }
    
    private fun showCreateRoomDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_create_room, null)
        
        val etRoomName = dialogView.findViewById<EditText>(R.id.etRoomName)
        val etRoomDescription = dialogView.findViewById<EditText>(R.id.etRoomDescription)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Create New Room")
            .setView(dialogView)
            .setPositiveButton("Create") { dialog, _ ->
                val name = etRoomName.text.toString().trim()
                val description = etRoomDescription.text.toString().trim()
                
                if (name.isEmpty()) {
                    Toast.makeText(this, "Room name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                lifecycleScope.launch {
                    val roomId = viewModel.createRoom(name, description)
                    // Navigate to AR screen with new room ID
                    val intent = Intent(this@HomeActivity, MainActivity::class.java).apply {
                        putExtra("ROOM_ID", roomId)
                        putExtra("ROOM_NAME", name)
                    }
                    startActivity(intent)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showEditRoomDialog(room: RoomEntity) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_room, null)
        val etName = dialogView.findViewById<EditText>(R.id.etRoomName)
        val etDescription = dialogView.findViewById<EditText>(R.id.etRoomDescription)
        
        // Pre-fill current values
        etName.setText(room.name)
        etDescription.setText(room.description)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Room")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                val description = etDescription.text.toString().trim()
                
                if (name.isEmpty()) {
                    Toast.makeText(this, "Room name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                lifecycleScope.launch {
                    val updatedRoom = room.copy(name = name, description = description)
                    viewModel.updateRoom(updatedRoom)
                    Toast.makeText(this@HomeActivity, "Room updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDeleteRoomDialog(room: RoomEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Room")
            .setMessage("Are you sure you want to delete '${room.name}' and all its measurements?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    viewModel.deleteRoom(room)
                    Toast.makeText(this@HomeActivity, "Room deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
