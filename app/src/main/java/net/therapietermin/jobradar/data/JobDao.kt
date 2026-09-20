package net.therapietermin.jobradar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY firstSeen DESC")
    fun observeAll(): Flow<List<Job>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(jobs: List<Job>)

    @Query("UPDATE jobs SET status=:status WHERE sourceId=:id")
    suspend fun setStatus(id: String, status: String)

    @Query("SELECT sourceId FROM jobs WHERE sourceId IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>
}
