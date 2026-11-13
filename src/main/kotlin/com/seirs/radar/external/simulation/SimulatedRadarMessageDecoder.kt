package com.seirs.radar.external.simulation

import com.seirs.radar.application.DecodedField
import com.seirs.radar.application.RadarMessageDecoder
import com.seirs.radar.data.RadarMessage
import com.seirs.radar.external.simulation.SimulatedRadarCodec.CODE_ADD
import com.seirs.radar.external.simulation.SimulatedRadarCodec.CODE_PLOT
import com.seirs.radar.external.simulation.SimulatedRadarCodec.CODE_UPDATE
import com.seirs.radar.external.simulation.SimulatedRadarCodec.TYPE_COMMAND
import com.seirs.radar.external.simulation.SimulatedRadarCodec.TYPE_PLOT
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * Simüle protokol için decoder. Gerçek (harici) decoder geldiğinde bu sınıf kaldırılır
 * ya da gerçek decoder @Primary işaretlenir.
 * Tanınmayan formatlarda ham içeriği hex olarak döner.
 */
@Component
class SimulatedRadarMessageDecoder : RadarMessageDecoder {

    override suspend fun decode(message: RadarMessage): List<DecodedField> {
        val raw = message.rawMessage
        val decoded = when {
            message.messageType == TYPE_PLOT && message.messageCode == CODE_PLOT ->
                SimulatedRadarCodec.decodePlot(raw)?.let { p ->
                    listOf(
                        DecodedField("Tur", p.rotation.toString(), null),
                        DecodedField("Plot sırası", p.plotIndex.toString(), null),
                        DecodedField("Azimut", p.azimuthDeg.fmt(), "°"),
                        DecodedField("Menzil", p.rangeKm.fmt(), "km"),
                        DecodedField("Yükseliş açısı", p.elevationDeg.fmt(), "°"),
                        DecodedField("Genlik", p.amplitudeDb.fmt(), "dB"),
                    )
                }
            message.messageType == TYPE_COMMAND && message.messageCode in setOf(CODE_ADD, CODE_UPDATE) ->
                SimulatedRadarCodec.decodeConfig(raw)?.let { c ->
                    listOf(
                        DecodedField("Dönme periyodu", c.rotationPeriodMs.toString(), "ms"),
                        DecodedField("Min plot / tur", c.minPlots.toString(), null),
                        DecodedField("Max plot / tur", c.maxPlots.toString(), null),
                    )
                }
            message.messageType == TYPE_COMMAND -> emptyList()
            else -> null
        }
        return decoded ?: listOf(DecodedField("Ham veri", raw.toHex(), "hex"))
    }

    private fun Float.fmt() = String.format(Locale.ROOT, "%.2f", this)

    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }
}
