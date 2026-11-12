package com.seirs.radar.presentation

import com.seirs.radar.application.AnchorPageRequest
import com.seirs.radar.application.RadarRecordService
import com.seirs.radar.data.Anchor
import com.seirs.radar.data.Direction
import com.seirs.radar.data.RadarRecordCriteria
import org.bson.types.ObjectId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1/radar-records")
class RadarRecordController(private val service: RadarRecordService) {

    /**
     * Tekrarlanan parametreler aynı alan için IN, farklı alanlar AND ile birleşir.
     * Sıralama sabit: actionTime DESC, _id DESC. Sonraki sayfa için önceki yanıttaki
     * nextAnchorId + nextAnchorValue birlikte gönderilir.
     */
    @GetMapping
    suspend fun list(
        @RequestParam(required = false) messageName: List<String>?,
        @RequestParam(required = false) messageType: List<String>?,
        @RequestParam(required = false) radarType: List<String>?,
        @RequestParam(required = false) direction: List<Direction>?,
        @RequestParam(required = false) messageCode: List<Int>?,
        @RequestParam(required = false) subCode: List<Int>?,
        @RequestParam(required = false) radarId: List<String>?,
        @RequestParam(required = false) actionTimeFrom: Instant?,
        @RequestParam(required = false) actionTimeTo: Instant?,
        @RequestParam(required = false) anchorId: String?,
        @RequestParam(required = false) anchorValue: Instant?,
        @RequestParam(defaultValue = AnchorPageRequest.DEFAULT_SIZE.toString()) size: Int,
    ): RadarPageResponse {
        if (size !in 1..AnchorPageRequest.MAX_SIZE) {
            throw InvalidRequestException("size must be between 1 and ${AnchorPageRequest.MAX_SIZE}")
        }
        if ((anchorId == null) != (anchorValue == null)) {
            throw InvalidRequestException("anchorId and anchorValue must be provided together")
        }
        val anchor = anchorId?.let { Anchor(parseObjectId(it, "anchorId"), anchorValue!!) }

        val criteria = RadarRecordCriteria(
            messageNames = messageName.orEmpty(),
            messageTypes = messageType.orEmpty(),
            radarTypes = radarType.orEmpty(),
            directions = direction.orEmpty(),
            messageCodes = messageCode.orEmpty(),
            subCodes = subCode.orEmpty(),
            radarIds = radarId.orEmpty(),
            actionTimeFrom = actionTimeFrom,
            actionTimeTo = actionTimeTo,
        )
        return service.search(criteria, AnchorPageRequest(anchor, size)).toResponse()
    }

    @GetMapping("/{id}")
    suspend fun get(@PathVariable id: String): RadarRecordDetailResponse =
        service.getDetail(parseObjectId(id, "id")).toResponse()

    private fun parseObjectId(value: String, name: String): ObjectId =
        if (ObjectId.isValid(value)) ObjectId(value) else throw InvalidRequestException("$name is not a valid ObjectId")
}
