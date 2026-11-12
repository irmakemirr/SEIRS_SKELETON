package com.seirs.radar.application

import com.seirs.radar.data.RadarMessage

/**
 * Harici decoder için genişleme noktası: ham mesajı, filtre dışı alanların listesine çevirir.
 *
 * Gerçek implementasyon Spring bean'i olarak eklendiğinde otomatik kullanılır; yoksa
 * [NoOpRadarMessageDecoder] devreye girer.
 *
 * Not: Reactive stack'te çalışır. Implementasyon bloklayan iş yapıyorsa
 * `withContext(Dispatchers.IO) { ... }` içinde yapmalıdır.
 */
fun interface RadarMessageDecoder {
    suspend fun decode(message: RadarMessage): List<DecodedField>
}

data class DecodedField(
    val label: String,
    val value: String,
    val unit: String?,
)

object NoOpRadarMessageDecoder : RadarMessageDecoder {
    override suspend fun decode(message: RadarMessage): List<DecodedField> = emptyList()
}
