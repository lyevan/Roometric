package com.infinity.roometric.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements WHERE roomId = :roomId ORDER BY timestamp DESC")
    fun getMeasurementsForRoom(roomId: Long): Flow<List<Measurement>>
    
    @Query("SELECT * FROM measurements WHERE roomId = :roomId AND planeType = :planeType")
    fun getMeasurementsByPlaneType(roomId: Long, planeType: PlaneType): Flow<List<Measurement>>
    
    @Query("SELECT * FROM measurements WHERE id = :measurementId")
    fun getMeasurementById(measurementId: Long): Measurement?
    
    @Insert
    fun insertMeasurement(measurement: Measurement): Long
    
    @Update
    fun updateMeasurement(measurement: Measurement)
    
    @Delete
    fun deleteMeasurement(measurement: Measurement)
    
    @Query("SELECT COUNT(*) FROM measurements WHERE roomId = :roomId AND planeType = 'FLOOR'")
    fun getFloorCount(roomId: Long): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM measurements WHERE roomId = :roomId AND planeType = 'WALL'")
    fun getWallCount(roomId: Long): Flow<Int>
    
    @Query("SELECT SUM(areaMeters) FROM measurements WHERE roomId = :roomId AND planeType = :planeType")
    fun getTotalAreaByPlaneType(roomId: Long, planeType: PlaneType): Flow<Float?>
}
