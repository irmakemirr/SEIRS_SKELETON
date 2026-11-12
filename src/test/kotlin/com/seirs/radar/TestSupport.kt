package com.seirs.radar

import com.seirs.radar.application.DecodedField
import com.seirs.radar.application.RadarMessageDecoder
import com.seirs.radar.data.Direction
import com.seirs.radar.data.RadarMessage
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.testcontainers.mongodb.MongoDBContainer

@TestConfiguration(proxyBeanMethods = false)
class TestSupportConfig {

    @Bean
    @ServiceConnection
    fun mongoContainer(): MongoDBContainer = MongoDBContainer("mongo:8.0")

    @Bean
    @Primary // simüle decoder yerine testte bu kullanılır
    fun testDecoder(): RadarMessageDecoder = RadarMessageDecoder { message ->
        listOf(
            DecodedField("payloadSize", message.rawMessage.size.toString(), "byte"),
            DecodedField("code", message.messageCode.toString(), null),
        )
    }
}

fun radarMessage(
    radarType: String = "SEARCH",
    radarId: String = "R-1",
    messageCode: Int = 10,
    subCode: Int? = null,
    direction: Direction = Direction.RECEIVED,
    messageType: String = "TRACK",
    messageName: String = "TrackReport",
    raw: ByteArray = byteArrayOf(0x01, 0x02, 0x7F, -0x80),
) = RadarMessage(raw, messageName, messageType, radarType, direction, messageCode, subCode, radarId)
