package eu.kanade.tachiyomi.data.track

import android.content.Context
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track

class MyAnimeList(
    context: Context,
    id: Int,
) : TrackService {
    override val id = id
    override val nameRes = R.string.myanimelist
    override val isLogged = false

    override fun getLogo() = R.drawable.ic_tracker_mal

    override fun getLogoColor() = R.color.myanimelist

    override fun getStatus(status: Int) = ""

    override fun getScoreList() = emptyList<String>()

    override fun displayScore(track: Track) = ""

    override fun supportsReadingDates() = false

    override suspend fun update(track: Track): Track = track

    override suspend fun bind(track: Track): Track = track

    override suspend fun search(query: String): List<TrackSearch> = emptyList()
}

class AniList(
    context: Context,
    id: Int,
) : TrackService {
    override val id = id
    override val nameRes = R.string.anilist
    override val isLogged = false

    override fun getLogo() = R.drawable.ic_tracker_anilist

    override fun getLogoColor() = R.color.anilist

    override fun getStatus(status: Int) = ""

    override fun getScoreList() = emptyList<String>()

    override fun displayScore(track: Track) = ""

    override fun supportsReadingDates() = false

    override suspend fun update(track: Track): Track = track

    override suspend fun bind(track: Track): Track = track

    override suspend fun search(query: String): List<TrackSearch> = emptyList()
}

class Kitsu(
    context: Context,
    id: Int,
) : TrackService {
    override val id = id
    override val nameRes = R.string.kitsu
    override val isLogged = false

    override fun getLogo() = R.drawable.ic_tracker_kitsu

    override fun getLogoColor() = R.color.kitsu

    override fun getStatus(status: Int) = ""

    override fun getScoreList() = emptyList<String>()

    override fun displayScore(track: Track) = ""

    override fun supportsReadingDates() = false

    override suspend fun update(track: Track): Track = track

    override suspend fun bind(track: Track): Track = track

    override suspend fun search(query: String): List<TrackSearch> = emptyList()
}

class Shikimori(
    context: Context,
    id: Int,
) : TrackService {
    override val id = id
    override val nameRes = R.string.shikimori
    override val isLogged = false

    override fun getLogo() = R.drawable.ic_tracker_shikimori

    override fun getLogoColor() = R.color.shikimori

    override fun getStatus(status: Int) = ""

    override fun getScoreList() = emptyList<String>()

    override fun displayScore(track: Track) = ""

    override fun supportsReadingDates() = false

    override suspend fun update(track: Track): Track = track

    override suspend fun bind(track: Track): Track = track

    override suspend fun search(query: String): List<TrackSearch> = emptyList()
}

class Bangumi(
    context: Context,
    id: Int,
) : TrackService {
    override val id = id
    override val nameRes = R.string.bangumi
    override val isLogged = false

    override fun getLogo() = R.drawable.ic_tracker_bangumi

    override fun getLogoColor() = R.color.bangumi

    override fun getStatus(status: Int) = ""

    override fun getScoreList() = emptyList<String>()

    override fun displayScore(track: Track) = ""

    override fun supportsReadingDates() = false

    override suspend fun update(track: Track): Track = track

    override suspend fun bind(track: Track): Track = track

    override suspend fun search(query: String): List<TrackSearch> = emptyList()
}

class Komga(
    context: Context,
    id: Int,
) : TrackService {
    override val id = id
    override val nameRes = R.string.komga
    override val isLogged = false

    override fun getLogo() = R.drawable.ic_tracker_komga

    override fun getLogoColor() = R.color.komga

    override fun getStatus(status: Int) = ""

    override fun getScoreList() = emptyList<String>()

    override fun displayScore(track: Track) = ""

    override fun supportsReadingDates() = false

    override suspend fun update(track: Track): Track = track

    override suspend fun bind(track: Track): Track = track

    override suspend fun search(query: String): List<TrackSearch> = emptyList()
}

class MangaUpdates(
    context: Context,
    id: Int,
) : TrackService {
    override val id = id
    override val nameRes = R.string.manga_updates
    override val isLogged = false

    override fun getLogo() = R.drawable.ic_tracker_manga_updates

    override fun getLogoColor() = R.color.manga_updates

    override fun getStatus(status: Int) = ""

    override fun getScoreList() = emptyList<String>()

    override fun displayScore(track: Track) = ""

    override fun supportsReadingDates() = false

    override suspend fun update(track: Track): Track = track

    override suspend fun bind(track: Track): Track = track

    override suspend fun search(query: String): List<TrackSearch> = emptyList()
}
