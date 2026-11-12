package com.seirs.radar.application

import com.seirs.radar.data.RadarRecordCriteria
import com.seirs.radar.data.RadarRecordDocument
import com.seirs.radar.data.RadarRecordQueryRepository
import com.seirs.radar.data.RadarRecordRepository
import com.seirs.radar.data.RadarRecordSummary
import org.bson.types.ObjectId
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.temporal.ChronoUnit

@Service
class RadarRecordService(
    private val repository: RadarRecordRepository,
    private val queryRepository: RadarRecordQueryRepository,
    decoderProvider: ObjectProvider<RadarMessageDecoder>,
) {
    private val decoder: RadarMessageDecoder = decoderProvider.getIfAvailable { NoOpRadarMessageDecoder }

    /** Uygulama içi insert; nonblocking (reactive driver üzerinde suspend). */
    suspend fun record(command: NewRadarRecord): RadarRecordDocument =
        repository.save(
            RadarRecordDocument(
                message = command.message,
                actionTime = command.actionTime.truncatedTo(ChronoUnit.MILLIS),
            ),
        )

    suspend fun getDetail(id: ObjectId): RadarRecordDetail {
        val record = repository.findById(id) ?: throw RadarRecordNotFoundException(id)
        return RadarRecordDetail(record, decoder.decode(record.message))
    }

    suspend fun search(criteria: RadarRecordCriteria, page: AnchorPageRequest): AnchorSlice<RadarRecordSummary> {
        val fetched = queryRepository.find(criteria, page.anchor, page.size + 1)
        val hasNext = fetched.size > page.size
        val items = if (hasNext) fetched.subList(0, page.size) else fetched
        val last = items.lastOrNull()
        return AnchorSlice(
            items = items,
            hasNext = hasNext,
            nextAnchorId = if (hasNext) last?.id else null,
            nextAnchorValue = if (hasNext) last?.actionTime else null,
        )
    }
}
