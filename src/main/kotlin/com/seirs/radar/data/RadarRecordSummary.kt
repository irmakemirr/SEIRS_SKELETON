package com.seirs.radar.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import java.time.Instant

/** Listeleme projection'ı: rawMessage veritabanından çekilmez. */
data class RadarRecordSummary(
    @Id val id: ObjectId,
    val message: RadarMessageSummary,
    val actionTime: Instant,
    val lastUpdated: Instant?,
)

data class RadarMessageSummary(
    val messageName: String,
    val messageType: String,
    val radarType: String,
    val direction: Direction,
    val messageCode: Int,
    val subCode: Int?,
    val radarId: String,
)
