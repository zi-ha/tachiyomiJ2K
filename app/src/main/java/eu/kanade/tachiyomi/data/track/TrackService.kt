package eu.kanade.tachiyomi.data.track

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.data.database.models.Track

interface TrackService {
    val id: Int

    @get:StringRes
    val nameRes: Int

    val isLogged: Boolean

    fun getLogo(): Int

    fun getLogoColor(): Int

    fun getStatus(status: Int): String?

    fun getScoreList(): List<String>

    fun displayScore(track: Track): String

    fun supportsReadingDates(): Boolean

    fun isMdList(): Boolean = false

    suspend fun update(track: Track): Track = track

    suspend fun bind(track: Track): Track = track

    suspend fun search(query: String): List<TrackSearch> = emptyList()
}

data class TrackSearch(
    val id: Long,
    val media_id: Long,
    val tracking_url: String,
    val title: String,
    val cover_url: String,
    val summary: String,
    val publishing_status: String,
    val publishing_type: String,
    val start_date: String,
    val total_chapters: Int,
)
