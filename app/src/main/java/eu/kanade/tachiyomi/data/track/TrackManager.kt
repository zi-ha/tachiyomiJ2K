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

    val services =
        listOf(
            MyAnimeList(context, MYANIMELIST),
            AniList(context, ANILIST),
            Kitsu(context, KITSU),
            Shikimori(context, SHIKIMORI),
            Bangumi(context, BANGUMI),
            Komga(context, KOMGA),
            MangaUpdates(context, MANGA_UPDATES),
        )

    fun getService(id: Int): TrackService? = services.find { it.id == id }
}
