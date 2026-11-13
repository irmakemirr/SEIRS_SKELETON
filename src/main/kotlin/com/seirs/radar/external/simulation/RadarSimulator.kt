package com.seirs.radar.external.simulation

import com.seirs.radar.application.NewRadarRecord
import com.seirs.radar.application.RadarAlreadyExistsException
import com.seirs.radar.application.RadarConfig
import com.seirs.radar.application.RadarGateway
import com.seirs.radar.application.RadarNotFoundException
import com.seirs.radar.application.RadarRecordService
import com.seirs.radar.application.RadarState
import com.seirs.radar.application.RadarStateConflictException
import com.seirs.radar.data.RadarMessage
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Uygulama içinde çalışan radar simülatörü (external katmanı).
 *
 * Her radar kendi coroutine'inde döner: her turda [minPlots, maxPlots] aralığında rastgele sayıda plot
 * üretir ve bunları azimut sırasına göre tur süresine yayarak RECEIVED kaydı olarak yazar.
 * Thread bloklanmaz (delay + reactive Mongo). Ayarlar bellekte tutulur; uygulama yeniden başlarsa radarlar silinir.
 */
@Component
class RadarSimulator(
    private val recordService: RadarRecordService,
) : RadarGateway {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = Clock.systemUTC()
    private val radars = ConcurrentHashMap<String, SimulatedRadar>()

    private class SimulatedRadar(@Volatile var config: RadarConfig) {
        /** null = durdurulmuş. Erişim `synchronized(this)` ile. */
        var job: Job? = null
        /** Durdur/başlat arasında tur sayısı devam eder. */
        @Volatile var rotation = 0
        val running: Boolean get() = synchronized(this) { job?.isActive == true }
    }

    override fun list(): List<RadarState> = radars.values.map { RadarState(it.config, it.running) }

    override suspend fun add(config: RadarConfig): RadarMessage {
        val radar = SimulatedRadar(config)
        if (radars.putIfAbsent(config.radarId, radar) != null) throw RadarAlreadyExistsException(config.radarId)
        launch(radar)
        log.info("Radar added: {}", config)
        return SimulatedRadarCodec.addCommand(config)
    }

    override suspend fun update(config: RadarConfig): RadarMessage {
        val radar = find(config.radarId)
        radar.config = config // çalışıyorsa bir sonraki turda geçerli olur
        log.info("Radar updated: {}", config)
        return SimulatedRadarCodec.updateCommand(config)
    }

    override suspend fun start(radarId: String): RadarMessage {
        val radar = find(radarId)
        synchronized(radar) {
            if (radar.job?.isActive == true) throw RadarStateConflictException("Radar already running: $radarId")
            launch(radar)
        }
        log.info("Radar started: {}", radarId)
        return SimulatedRadarCodec.startCommand(radarId, radar.config.radarType)
    }

    override suspend fun stop(radarId: String): RadarMessage {
        val radar = find(radarId)
        synchronized(radar) {
            val job = radar.job
            if (job?.isActive != true) throw RadarStateConflictException("Radar already stopped: $radarId")
            job.cancel()
            radar.job = null
        }
        log.info("Radar stopped: {}", radarId)
        return SimulatedRadarCodec.stopCommand(radarId, radar.config.radarType)
    }

    override suspend fun remove(radarId: String): RadarMessage {
        val radar = radars.remove(radarId) ?: throw RadarNotFoundException(radarId)
        synchronized(radar) {
            radar.job?.cancel()
            radar.job = null
        }
        log.info("Radar removed: {}", radarId)
        return SimulatedRadarCodec.removeCommand(radarId, radar.config.radarType)
    }

    private fun find(radarId: String) = radars[radarId] ?: throw RadarNotFoundException(radarId)

    private fun launch(radar: SimulatedRadar) = synchronized(radar) {
        radar.job = scope.launch { run(radar) }
    }

    private suspend fun CoroutineScope.run(radar: SimulatedRadar) {
        while (isActive) {
            val rotation = radar.rotation
            val config = radar.config
            val rotationStart = clock.millis()
            val plotCount = Random.nextInt(config.minPlots, config.maxPlots + 1)
            val azimuths = List(plotCount) { Random.nextDouble(0.0, 360.0) }.sorted()

            azimuths.forEachIndexed { index, azimuth ->
                val dueAt = rotationStart + (azimuth / 360.0 * config.rotationPeriodMs).toLong()
                delay((dueAt - clock.millis()).coerceAtLeast(0))
                emit(config, rotation, index, azimuth)
            }
            delay((rotationStart + config.rotationPeriodMs - clock.millis()).coerceAtLeast(0))
            radar.rotation++
        }
    }

    private suspend fun emit(config: RadarConfig, rotation: Int, index: Int, azimuth: Double) {
        val plot = SimulatedRadarCodec.Plot(
            rotation = rotation,
            plotIndex = index,
            azimuthDeg = azimuth.toFloat(),
            rangeKm = Random.nextDouble(1.0, 250.0).toFloat(),
            elevationDeg = Random.nextDouble(0.0, 30.0).toFloat(),
            amplitudeDb = Random.nextDouble(-20.0, 40.0).toFloat(),
        )
        try {
            recordService.record(
                NewRadarRecord(SimulatedRadarCodec.plotMessage(config.radarId, config.radarType, plot), clock.instant()),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Plot could not be recorded for radar {}: {}", config.radarId, e.message)
        }
    }

    @PreDestroy
    fun shutdown() {
        scope.cancel()
    }
}
