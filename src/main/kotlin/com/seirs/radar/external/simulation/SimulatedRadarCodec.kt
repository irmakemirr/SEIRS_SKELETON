package com.seirs.radar.external.simulation

import com.seirs.radar.application.RadarConfig
import com.seirs.radar.data.Direction
import com.seirs.radar.data.RadarMessage
import java.nio.ByteBuffer

/**
 * Simüle radar protokolü (big-endian ikili format).
 *
 * PLOT (RECEIVED, code 100): rotation:Int, plotIndex:Short, azimuthDeg:Float, rangeKm:Float, elevationDeg:Float, amplitudeDb:Float
 * COMMAND (SENT):
 *   1 AddRadar / 2 UpdateRadarConfig: rotationPeriodMs:Int, minPlots:Int, maxPlots:Int
 *   3 RemoveRadar / 4 StartRadar / 5 StopRadar: boş gövde
 */
object SimulatedRadarCodec {
    const val TYPE_PLOT = "PLOT"
    const val TYPE_COMMAND = "COMMAND"

    const val CODE_PLOT = 100
    const val CODE_ADD = 1
    const val CODE_UPDATE = 2
    const val CODE_REMOVE = 3
    const val CODE_START = 4
    const val CODE_STOP = 5

    private const val PLOT_SIZE = 4 + 2 + 4 * 4
    private const val CONFIG_SIZE = 3 * 4

    data class Plot(
        val rotation: Int,
        val plotIndex: Int,
        val azimuthDeg: Float,
        val rangeKm: Float,
        val elevationDeg: Float,
        val amplitudeDb: Float,
    )

    data class ConfigPayload(val rotationPeriodMs: Int, val minPlots: Int, val maxPlots: Int)

    fun plotMessage(radarId: String, radarType: String, plot: Plot): RadarMessage {
        val raw = ByteBuffer.allocate(PLOT_SIZE)
            .putInt(plot.rotation)
            .putShort(plot.plotIndex.toShort())
            .putFloat(plot.azimuthDeg)
            .putFloat(plot.rangeKm)
            .putFloat(plot.elevationDeg)
            .putFloat(plot.amplitudeDb)
            .array()
        return RadarMessage(raw, "Plot", TYPE_PLOT, radarType, Direction.RECEIVED, CODE_PLOT, null, radarId)
    }

    fun addCommand(config: RadarConfig) = configCommand(config, CODE_ADD, "AddRadar")

    fun updateCommand(config: RadarConfig) = configCommand(config, CODE_UPDATE, "UpdateRadarConfig")

    fun removeCommand(radarId: String, radarType: String) = emptyCommand(radarId, radarType, CODE_REMOVE, "RemoveRadar")

    fun startCommand(radarId: String, radarType: String) = emptyCommand(radarId, radarType, CODE_START, "StartRadar")

    fun stopCommand(radarId: String, radarType: String) = emptyCommand(radarId, radarType, CODE_STOP, "StopRadar")

    private fun emptyCommand(radarId: String, radarType: String, code: Int, name: String) =
        RadarMessage(ByteArray(0), name, TYPE_COMMAND, radarType, Direction.SENT, code, null, radarId)

    private fun configCommand(config: RadarConfig, code: Int, name: String): RadarMessage {
        val raw = ByteBuffer.allocate(CONFIG_SIZE)
            .putInt(config.rotationPeriodMs.toInt())
            .putInt(config.minPlots)
            .putInt(config.maxPlots)
            .array()
        return RadarMessage(raw, name, TYPE_COMMAND, config.radarType, Direction.SENT, code, null, config.radarId)
    }

    fun decodePlot(raw: ByteArray): Plot? {
        if (raw.size != PLOT_SIZE) return null
        val b = ByteBuffer.wrap(raw)
        return Plot(b.int, b.short.toInt(), b.float, b.float, b.float, b.float)
    }

    fun decodeConfig(raw: ByteArray): ConfigPayload? {
        if (raw.size != CONFIG_SIZE) return null
        val b = ByteBuffer.wrap(raw)
        return ConfigPayload(b.int, b.int, b.int)
    }
}
