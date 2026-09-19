package net.therapietermin.jobradar.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY score DESC, firstSeen DESC")
    fun observeAll(): Flow<List<Job>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(jobs: List<Job>)

    @Query("UPDATE jobs SET status=:status WHERE sourceId=:id")
    suspend fun setStatus(id: String, status: String)
}
