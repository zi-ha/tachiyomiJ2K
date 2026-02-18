package eu.kanade.tachiyomi.data.track

import android.content.Context

class TrackManager(
    context: Context,
) {
    companion object {
        const val MYANIMELIST = 1
        const val ANILIST = 2
        const val KITSU = 3
        const val SHIKIMORI = 4
        const val BANGUMI = 5
        const val KOMGA = 6
        const val MANGA_UPDATES = 7
    }

    val services = emptyList<TrackService>()

    fun getService(id: Int): TrackService? = services.find { it.id == id }
}
