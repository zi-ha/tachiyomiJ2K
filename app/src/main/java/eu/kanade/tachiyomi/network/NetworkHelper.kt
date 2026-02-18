package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

class NetworkHelper(
    context: Context,
) {
    private val cacheDir = File(context.cacheDir, "network_cache")
    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

    val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .cache(Cache(cacheDir, cacheSize))
            .build()

    val cloudflareClient: OkHttpClient = client
}
