package com.seirs.radar.external.simulation

import com.seirs.radar.application.RadarConfig
import com.seirs.radar.radarMessage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SimulatedRadarMessageDecoderTest {

    private val decoder = SimulatedRadarMessageDecoder()

    @Test
    fun `decodes plot fields`(): Unit = runBlocking {
        val plot = SimulatedRadarCodec.Plot(3, 7, 123.456f, 42.5f, 5.25f, -3.0f)
        val fields = decoder.decode(SimulatedRadarCodec.plotMessage("R-1", "SEARCH", plot))

        assertEquals(
            listOf("Tur" to "3", "Plot sırası" to "7", "Azimut" to "123.46", "Menzil" to "42.50",
                "Yükseliş açısı" to "5.25", "Genlik" to "-3.00"),
            fields.map { it.label to it.value },
        )
        assertEquals("°", fields[2].unit)
        assertEquals("km", fields[3].unit)
    }

    @Test
    fun `decodes config commands`(): Unit = runBlocking {
        val config = RadarConfig("R-1", "SEARCH", 4000, 5, 20)
        val fields = decoder.decode(SimulatedRadarCodec.updateCommand(config))
        assertEquals(listOf("4000", "5", "20"), fields.map { it.value })
        assertEquals(emptyList(), decoder.decode(SimulatedRadarCodec.removeCommand("R-1", "SEARCH")))
    }

    @Test
    fun `falls back to hex for unknown formats`(): Unit = runBlocking {
        val fields = decoder.decode(radarMessage(messageType = "OTHER", raw = byteArrayOf(0x01, -0x01)))
        assertEquals(listOf("01 FF"), fields.map { it.value })
    }
}
