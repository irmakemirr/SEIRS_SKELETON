package com.seirs.radar.data

import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.flow.toList
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.time.Instant

/** Filtre değerleri: boş liste = o alana filtre uygulanmaz; dolu liste = $in. Alanlar arası AND. */
data class RadarRecordCriteria(
    val messageNames: List<String> = emptyList(),
    val messageTypes: List<String> = emptyList(),
    val radarTypes: List<String> = emptyList(),
    val directions: List<Direction> = emptyList(),
    val messageCodes: List<Int> = emptyList(),
    val subCodes: List<Int> = emptyList(),
    val radarIds: List<String> = emptyList(),
    val actionTimeFrom: Instant? = null,
    val actionTimeTo: Instant? = null,
)

data class Anchor(val id: ObjectId, val actionTime: Instant)

@Repository
class RadarRecordQueryRepository(private val template: ReactiveMongoTemplate) {

    /** actionTime DESC, _id DESC sıralı; anchor'dan sonraki en fazla [limit] kaydı döner. */
    suspend fun find(criteria: RadarRecordCriteria, anchor: Anchor?, limit: Int): List<RadarRecordSummary> {
        val query = Query(buildCriteria(criteria, anchor))
            .with(Sort.by(Sort.Order.desc("actionTime"), Sort.Order.desc("_id")))
            .limit(limit)
        query.fields().exclude("message.rawMessage")
        return template.find(query, RadarRecordSummary::class.java, RADAR_RECORDS_COLLECTION).asFlow().toList()
    }

    private fun buildCriteria(c: RadarRecordCriteria, anchor: Anchor?): Criteria {
        val parts = mutableListOf<Criteria>()
        fun inIfPresent(field: String, values: List<Any>) {
            if (values.isNotEmpty()) parts += Criteria.where("message.$field").`in`(values)
        }
        inIfPresent("messageName", c.messageNames)
        inIfPresent("messageType", c.messageTypes)
        inIfPresent("radarType", c.radarTypes)
        inIfPresent("direction", c.directions.map { it.name })
        inIfPresent("messageCode", c.messageCodes)
        inIfPresent("subCode", c.subCodes)
        inIfPresent("radarId", c.radarIds)

        if (c.actionTimeFrom != null || c.actionTimeTo != null) {
            val time = Criteria.where("actionTime")
            c.actionTimeFrom?.let { time.gte(it) }
            c.actionTimeTo?.let { time.lt(it) }
            parts += time
        }

        if (anchor != null) {
            parts += Criteria().orOperator(
                Criteria.where("actionTime").lt(anchor.actionTime),
                Criteria.where("actionTime").`is`(anchor.actionTime).and("_id").lt(anchor.id),
            )
        }
        return if (parts.isEmpty()) Criteria() else Criteria().andOperator(parts)
    }
}
