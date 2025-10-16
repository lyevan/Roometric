package com.infinity.roometric.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.infinity.roometric.data.AppDatabase
import com.infinity.roometric.data.Measurement
import com.infinity.roometric.data.PlaneType
import com.infinity.roometric.data.RoomRepository
import com.infinity.roometric.data.NodePosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MeasurementViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: RoomRepository
    private val gson = Gson()
    
    init {
        val database = AppDatabase.getDatabase(application)
        repository = RoomRepository(database.roomDao(), database.measurementDao())
    }
    
    fun getMeasurementsForRoom(roomId: Long): Flow<List<Measurement>> {
        return repository.getMeasurementsForRoom(roomId)
    }
    
    fun getFloorMeasurements(roomId: Long): Flow<List<Measurement>> {
        return repository.getMeasurementsByPlaneType(roomId, PlaneType.FLOOR)
    }
    
    fun getWallMeasurements(roomId: Long): Flow<List<Measurement>> {
        return repository.getMeasurementsByPlaneType(roomId, PlaneType.WALL)
    }
    
    suspend fun saveMeasurement(
        roomId: Long,
        name: String,
        description: String,
        planeType: PlaneType,
        heightMeters: Float,
        widthMeters: Float,
        node1X: Float, node1Y: Float, node1Z: Float,
        node2X: Float, node2Y: Float, node2Z: Float,
        node3X: Float, node3Y: Float, node3Z: Float,
        node4X: Float, node4Y: Float, node4Z: Float
    ): Long {
        val nodePositions = listOf(
            NodePosition(node1X, node1Y, node1Z),
            NodePosition(node2X, node2Y, node2Z),
            NodePosition(node3X, node3Y, node3Z),
            NodePosition(node4X, node4Y, node4Z)
        )
        
        val nodePositionsJson = gson.toJson(nodePositions)
        val areaMeters = heightMeters * widthMeters
        
        val measurement = Measurement(
            roomId = roomId,
            name = name,
            description = description,
            planeType = planeType,
            areaMeters = areaMeters,
            heightMeters = heightMeters,
            widthMeters = widthMeters,
            nodePositions = nodePositionsJson,
            timestamp = System.currentTimeMillis()
        )
        
        return repository.insertMeasurement(measurement)
    }
    
    suspend fun updateMeasurement(measurement: Measurement) {
        repository.updateMeasurement(measurement)
    }
    
    suspend fun deleteMeasurement(measurement: Measurement) {
        repository.deleteMeasurement(measurement)
    }
}
