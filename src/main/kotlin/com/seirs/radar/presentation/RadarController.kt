package com.seirs.radar.presentation

import com.seirs.radar.application.RadarConfig
import com.seirs.radar.application.RadarControlService
import com.seirs.radar.application.RadarState
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class RadarResponse(
    val radarId: String,
    val radarType: String,
    val rotationPeriodMs: Long,
    val minPlots: Int,
    val maxPlots: Int,
    val running: Boolean,
)

data class CreateRadarRequest(
    val radarId: String,
    val radarType: String,
    val rotationPeriodMs: Long,
    val minPlots: Int,
    val maxPlots: Int,
)

data class UpdateRadarRequest(
    val rotationPeriodMs: Long,
    val minPlots: Int,
    val maxPlots: Int,
)

/** Radar (simülasyon) yönetimi. Her komut radar_records'a direction=SENT olarak yazılır. */
@RestController
@RequestMapping("/api/v1/radars")
class RadarController(private val service: RadarControlService) {

    @GetMapping
    fun list(): List<RadarResponse> = service.list().map { it.toResponse() }

    /** Eklenen radar hemen çalışmaya başlar. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun create(@RequestBody request: CreateRadarRequest): RadarResponse =
        service.add(
            RadarConfig(request.radarId, request.radarType, request.rotationPeriodMs, request.minPlots, request.maxPlots),
        ).toResponse()

    /** Yük ayarlarını değiştirir; çalışan radarda bir sonraki turda geçerli olur. */
    @PutMapping("/{radarId}")
    suspend fun update(@PathVariable radarId: String, @RequestBody request: UpdateRadarRequest): RadarResponse {
        val current = service.get(radarId).config
        return service.update(
            current.copy(rotationPeriodMs = request.rotationPeriodMs, minPlots = request.minPlots, maxPlots = request.maxPlots),
        ).toResponse()
    }

    @PostMapping("/{radarId}/start")
    suspend fun start(@PathVariable radarId: String): RadarResponse = service.start(radarId).toResponse()

    @PostMapping("/{radarId}/stop")
    suspend fun stop(@PathVariable radarId: String): RadarResponse = service.stop(radarId).toResponse()

    @DeleteMapping("/{radarId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun delete(@PathVariable radarId: String) = service.remove(radarId)

    private fun RadarState.toResponse() = with(config) {
        RadarResponse(radarId, radarType, rotationPeriodMs, minPlots, maxPlots, running)
    }
}
