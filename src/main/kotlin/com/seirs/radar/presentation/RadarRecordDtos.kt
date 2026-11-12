package com.seirs.radar.presentation

import com.seirs.radar.application.AnchorSlice
import com.seirs.radar.application.DecodedField
import com.seirs.radar.application.RadarRecordDetail
import com.seirs.radar.data.Direction
import com.seirs.radar.data.RadarRecordSummary
import java.time.Instant

data class RadarMessageResponse(
    val messageName: String,
    val messageType: String,
    val radarType: String,
    val direction: Direction,
    val messageCode: Int,
    val subCode: Int?,
    val radarId: String,
)

/** Liste öğesi: rawMessage içermez. */
data class RadarRecordSummaryResponse(
    val id: String,
    val message: RadarMessageResponse,
    val actionTime: Instant,
    val lastUpdated: Instant?,
)

data class DecodedFieldResponse(val label: String, val value: String, val unit: String?)

/** Detay: ham mesaj yerine decoder çıktısı döner; rawMessage API'ye çıkmaz. */
data class RadarRecordDetailResponse(
    val id: String,
    val message: RadarMessageResponse,
    val actionTime: Instant,
    val lastUpdated: Instant?,
    val fields: List<DecodedFieldResponse>,
)

data class RadarPageResponse(
    val items: List<RadarRecordSummaryResponse>,
    val hasNext: Boolean,
    val nextAnchorId: String?,
    val nextAnchorValue: Instant?,
)

fun RadarRecordSummary.toResponse() = RadarRecordSummaryResponse(
    id = id.toHexString(),
    message = with(message) {
        RadarMessageResponse(messageName, messageType, radarType, direction, messageCode, subCode, radarId)
    },
    actionTime = actionTime,
    lastUpdated = lastUpdated,
)

fun RadarRecordDetail.toResponse() = RadarRecordDetailResponse(
    id = requireNotNull(record.id).toHexString(),
    message = with(record.message) {
        RadarMessageResponse(messageName, messageType, radarType, direction, messageCode, subCode, radarId)
    },
    actionTime = record.actionTime,
    lastUpdated = record.lastUpdated,
    fields = decodedFields.map(DecodedField::toResponse),
)

private fun DecodedField.toResponse() = DecodedFieldResponse(label, value, unit)

fun AnchorSlice<RadarRecordSummary>.toResponse() = RadarPageResponse(
    items = items.map { it.toResponse() },
    hasNext = hasNext,
    nextAnchorId = nextAnchorId?.toHexString(),
    nextAnchorValue = nextAnchorValue,
)
