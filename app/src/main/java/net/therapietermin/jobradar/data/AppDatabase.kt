package net.therapietermin.jobradar.data

import android.content.Context
import androidx.room.*

@Database(entities = [Job::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobs(): JobDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "jobradar.db"
            ).build().also { instance = it }
        }
    }
}
