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
}
