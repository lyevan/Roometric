package com.infinity.roometric.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY timestamp DESC")
    fun getAllRooms(): Flow<List<RoomEntity>>
    
    @Query("SELECT * FROM rooms WHERE id = :roomId")
    fun getRoomById(roomId: Long): RoomEntity?
    
    @Insert
    fun insertRoom(room: RoomEntity): Long
    
    @Update
    fun updateRoom(room: RoomEntity)
    
    @Delete
    fun deleteRoom(room: RoomEntity)
}
