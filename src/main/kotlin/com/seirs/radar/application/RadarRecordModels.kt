package com.seirs.radar.application

import com.seirs.radar.data.Anchor
import com.seirs.radar.data.RadarMessage
import com.seirs.radar.data.RadarRecordDocument
import org.bson.types.ObjectId
import java.time.Instant

data class NewRadarRecord(
    val message: RadarMessage,
    val actionTime: Instant,
)

data class AnchorPageRequest(
    val anchor: Anchor?,
    val size: Int,
) {
    init {
        require(size in 1..MAX_SIZE) { "size must be between 1 and $MAX_SIZE" }
    }

    companion object {
        const val DEFAULT_SIZE = 50
        const val MAX_SIZE = 200
    }
}

data class AnchorSlice<T>(
    val items: List<T>,
    val hasNext: Boolean,
    val nextAnchorId: ObjectId?,
    val nextAnchorValue: Instant?,
)

data class RadarRecordDetail(
    val record: RadarRecordDocument,
    val decodedFields: List<DecodedField>,
)

class RadarRecordNotFoundException(val id: ObjectId) : RuntimeException("Radar record not found: $id")
