package eu.kanade.tachiyomi.data.preference.models

import kotlinx.serialization.Serializable

@Serializable
data class LocalLocation(
    val directory: String,
    val name: String,
    val enabled: Boolean = true,
)
