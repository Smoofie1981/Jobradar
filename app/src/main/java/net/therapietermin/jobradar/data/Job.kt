package net.therapietermin.jobradar.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class Job(
    @PrimaryKey val sourceId: String,
    val source: String,
    val title: String,
    val employer: String,
    val city: String,
    val description: String = "",
    val pay: String? = null,
    val permanent: Boolean? = null,
    val url: String,
    val score: Int = 0,
    val reasons: String = "",
    val status: String = "NEW",
    val firstSeen: Long = System.currentTimeMillis()
)
