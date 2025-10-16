package com.infinity.roometric.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.infinity.roometric.data.AppDatabase
import com.infinity.roometric.data.RoomEntity
import com.infinity.roometric.data.RoomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class RoomViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: RoomRepository
    val allRooms: Flow<List<RoomEntity>>
    
    init {
        val database = AppDatabase.getDatabase(application)
        repository = RoomRepository(database.roomDao(), database.measurementDao())
        allRooms = repository.getAllRooms()
    }
    
    suspend fun createRoom(name: String, description: String): Long {
        val room = RoomEntity(
            name = name,
            description = description,
            timestamp = System.currentTimeMillis()
        )
        return repository.insertRoom(room)
    }
    
    suspend fun updateRoom(room: RoomEntity) {
        repository.updateRoom(room)
    }
    
    suspend fun deleteRoom(room: RoomEntity) {
        repository.deleteRoom(room)
    }
}
