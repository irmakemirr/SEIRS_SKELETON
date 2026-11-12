package com.seirs.radar.application

import com.seirs.radar.data.RadarMessage

/** Bir radarın simülasyon/çalışma ayarları. */
data class RadarConfig(
    val radarId: String,
    val radarType: String,
    val rotationPeriodMs: Long,
    val minPlots: Int,
    val maxPlots: Int,
) {
    fun validate(): RadarConfig {
        fun check(ok: Boolean, msg: String) { if (!ok) throw InvalidRadarConfigException(msg) }
        check(RADAR_ID_PATTERN.matches(radarId), "radarId must match ${RADAR_ID_PATTERN.pattern}")
        check(radarType.isNotBlank() && radarType.length <= 32, "radarType must be 1..32 characters")
        check(rotationPeriodMs in MIN_PERIOD_MS..MAX_PERIOD_MS, "rotationPeriodMs must be between $MIN_PERIOD_MS and $MAX_PERIOD_MS")
        check(minPlots in 0..MAX_PLOTS, "minPlots must be between 0 and $MAX_PLOTS")
        check(maxPlots in minPlots..MAX_PLOTS, "maxPlots must be between minPlots and $MAX_PLOTS")
        return this
    }

    companion object {
        val RADAR_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,32}")
        const val MIN_PERIOD_MS = 100L
        const val MAX_PERIOD_MS = 60_000L
        const val MAX_PLOTS = 500
    }
}

/**
 * Radarlara erişim sınırı (external katmanı implemente eder).
 * Komut gönderen metotlar, radara gönderilen mesajı döner; application bunu SENT olarak kaydeder.
 */
interface RadarGateway {
    fun list(): List<RadarState>
    /** Radarı ekler ve hemen çalıştırır. */
    suspend fun add(config: RadarConfig): RadarMessage
    /** Ayarları günceller; çalışan radarda bir sonraki turda geçerli olur. */
    suspend fun update(config: RadarConfig): RadarMessage
    suspend fun remove(radarId: String): RadarMessage
    suspend fun start(radarId: String): RadarMessage
    suspend fun stop(radarId: String): RadarMessage
}

data class RadarState(val config: RadarConfig, val running: Boolean)

class InvalidRadarConfigException(message: String) : RuntimeException(message)
class RadarAlreadyExistsException(val radarId: String) : RuntimeException("Radar already exists: $radarId")
class RadarNotFoundException(val radarId: String) : RuntimeException("Radar not found: $radarId")
class RadarStateConflictException(message: String) : RuntimeException(message)
