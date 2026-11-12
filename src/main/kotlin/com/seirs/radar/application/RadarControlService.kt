package com.seirs.radar.application

import com.seirs.radar.data.RadarMessage
import org.springframework.stereotype.Service
import java.time.Clock

/** Komuta-kontrol tarafı: radar komutlarını gönderir ve her komutu SENT kaydı olarak saklar. */
@Service
class RadarControlService(
    private val gateway: RadarGateway,
    private val recordService: RadarRecordService,
) {
    private val clock: Clock = Clock.systemUTC()

    fun list(): List<RadarState> = gateway.list().sortedBy { it.config.radarId }

    fun get(radarId: String): RadarState =
        gateway.list().find { it.config.radarId == radarId } ?: throw RadarNotFoundException(radarId)

    suspend fun add(config: RadarConfig): RadarState {
        send(gateway.add(config.validate()))
        return get(config.radarId)
    }

    suspend fun update(config: RadarConfig): RadarState {
        send(gateway.update(config.validate()))
        return get(config.radarId)
    }

    suspend fun start(radarId: String): RadarState {
        send(gateway.start(radarId))
        return get(radarId)
    }

    suspend fun stop(radarId: String): RadarState {
        send(gateway.stop(radarId))
        return get(radarId)
    }

    suspend fun remove(radarId: String) = send(gateway.remove(radarId))

    private suspend fun send(message: RadarMessage) {
        recordService.record(NewRadarRecord(message, clock.instant()))
    }
}
