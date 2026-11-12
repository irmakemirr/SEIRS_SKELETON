package com.seirs.radar.data

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

const val RADAR_RECORDS_COLLECTION = "radar_records"

/** Pagination sıralaması (actionTime DESC, _id DESC) için tek indeks. Filtre indeksleri bilinçli olarak yok. */
@Document(RADAR_RECORDS_COLLECTION)
@CompoundIndex(name = "actionTime_desc_id_desc", def = "{'actionTime': -1, '_id': -1}")
data class RadarRecordDocument(
    @Id val id: ObjectId? = null,
    val message: RadarMessage,
    val actionTime: Instant,
    @LastModifiedDate val lastUpdated: Instant? = null,
)

/** rawMessage: ByteArray → MongoDB'de BinData olarak saklanır. */
data class RadarMessage(
    val rawMessage: ByteArray,
    val messageName: String,
    val messageType: String,
    val radarType: String,
    val direction: Direction,
    val messageCode: Int,
    val subCode: Int?,
    val radarId: String,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is RadarMessage &&
            rawMessage.contentEquals(other.rawMessage) &&
            messageName == other.messageName &&
            messageType == other.messageType &&
            radarType == other.radarType &&
            direction == other.direction &&
            messageCode == other.messageCode &&
            subCode == other.subCode &&
            radarId == other.radarId)

    override fun hashCode(): Int {
        var result = rawMessage.contentHashCode()
        result = 31 * result + messageName.hashCode()
        result = 31 * result + messageType.hashCode()
        result = 31 * result + radarType.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + messageCode
        result = 31 * result + (subCode ?: 0)
        result = 31 * result + radarId.hashCode()
        return result
    }
}

enum class Direction { RECEIVED, SENT }
