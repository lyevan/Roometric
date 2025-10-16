package com.infinity.roometric.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

enum class PlaneType {
    FLOOR,
    WALL
}

data class NodePosition(
    val x: Float,
    val y: Float,
    val z: Float
)

@Entity(
    tableName = "measurements",
    foreignKeys = [ForeignKey(
        entity = RoomEntity::class,
        parentColumns = ["id"],
        childColumns = ["roomId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Measurement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val roomId: Long,
    val name: String,
    val description: String = "",
    val planeType: PlaneType,
    val areaMeters: Float,
    val heightMeters: Float,
    val widthMeters: Float,
    val nodePositions: String, // Serialized JSON array of NodePosition
    val timestamp: Long = System.currentTimeMillis()
)
