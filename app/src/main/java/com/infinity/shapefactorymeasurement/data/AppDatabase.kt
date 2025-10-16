package com.infinity.roometric.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson

class Converters {
    @TypeConverter
    fun fromPlaneType(value: PlaneType): String {
        return value.name
    }
    
    @TypeConverter
    fun toPlaneType(value: String): PlaneType {
        return PlaneType.valueOf(value)
    }
    
    @TypeConverter
    fun fromNodePositionList(value: List<NodePosition>): String {
        return Gson().toJson(value)
    }
    
    @TypeConverter
    fun toNodePositionList(value: String): List<NodePosition> {
        val type = object : com.google.gson.reflect.TypeToken<List<NodePosition>>() {}.type
        return Gson().fromJson(value, type)
    }
}

@Database(
    entities = [RoomEntity::class, Measurement::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun measurementDao(): MeasurementDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "measurement_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
