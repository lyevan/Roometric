package com.infinity.roometric.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RoomRepository(private val roomDao: RoomDao, private val measurementDao: MeasurementDao) {
    
    fun getAllRooms(): Flow<List<RoomEntity>> = roomDao.getAllRooms()
    
    suspend fun getRoomById(roomId: Long): RoomEntity? = withContext(Dispatchers.IO) {
        roomDao.getRoomById(roomId)
    }
    
    suspend fun insertRoom(room: RoomEntity): Long = withContext(Dispatchers.IO) {
        roomDao.insertRoom(room)
    }
    
    suspend fun updateRoom(room: RoomEntity) = withContext(Dispatchers.IO) {
        roomDao.updateRoom(room)
    }
    
    suspend fun deleteRoom(room: RoomEntity) = withContext(Dispatchers.IO) {
        roomDao.deleteRoom(room)
    }    
    fun getMeasurementsForRoom(roomId: Long): Flow<List<Measurement>> = 
        measurementDao.getMeasurementsForRoom(roomId)
    
    fun getMeasurementsByPlaneType(roomId: Long, planeType: PlaneType): Flow<List<Measurement>> =
        measurementDao.getMeasurementsByPlaneType(roomId, planeType)
    
    suspend fun insertMeasurement(measurement: Measurement): Long = withContext(Dispatchers.IO) {
        measurementDao.insertMeasurement(measurement)
    }
    
    suspend fun updateMeasurement(measurement: Measurement) = withContext(Dispatchers.IO) {
        measurementDao.updateMeasurement(measurement)
    }
    
    suspend fun deleteMeasurement(measurement: Measurement) = withContext(Dispatchers.IO) {
        measurementDao.deleteMeasurement(measurement)
    }
    
    fun getFloorCount(roomId: Long): Flow<Int> = measurementDao.getFloorCount(roomId)
    
    fun getWallCount(roomId: Long): Flow<Int> = measurementDao.getWallCount(roomId)
    
    fun getTotalAreaByPlaneType(roomId: Long, planeType: PlaneType): Flow<Float?> =
        measurementDao.getTotalAreaByPlaneType(roomId, planeType)
}
