package com.seirs.radar

import com.seirs.radar.application.NewRadarRecord
import com.seirs.radar.application.RadarRecordService
import com.seirs.radar.data.RADAR_RECORDS_COLLECTION
import com.seirs.radar.data.RadarRecordDocument
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSupportConfig::class)
class RadarRecordControllerIT {

    @LocalServerPort var port: Int = 0
    @Autowired lateinit var service: RadarRecordService
    @Autowired lateinit var template: ReactiveMongoTemplate

    private val client by lazy { WebTestClient.bindToServer().baseUrl("http://localhost:$port").build() }
    private val base: Instant = Instant.parse("2026-11-12T10:00:00Z")

    @BeforeEach
    fun clean(): Unit = runBlocking {
        template.remove(Query(), RADAR_RECORDS_COLLECTION).awaitSingle()
    }

    private fun seed(vararg radarTypes: String): List<RadarRecordDocument> = runBlocking {
        radarTypes.mapIndexed { i, type ->
            service.record(NewRadarRecord(radarMessage(radarType = type), base.plusSeconds(i.toLong())))
        }
    }

    @Test
    fun `list filters, paginates with anchor and never exposes rawMessage`() {
        seed("A", "B", "A", "A")

        val first = client.get().uri("/api/v1/radar-records?radarType=A&size=2").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(2)
            .jsonPath("$.items[0].message.radarType").isEqualTo("A")
            .jsonPath("$.items[0].message.rawMessage").doesNotExist()
            .jsonPath("$.items[0].actionTime").isEqualTo("2026-11-12T10:00:03Z")
            .jsonPath("$.hasNext").isEqualTo(true)
            .returnResult()

        val body = String(first.responseBodyContent!!)
        val anchorId = Regex(""""nextAnchorId":"([0-9a-f]{24})"""").find(body)!!.groupValues[1]
        val anchorValue = Regex(""""nextAnchorValue":"([^"]+)"""").find(body)!!.groupValues[1]

        client.get()
            .uri {
                it.path("/api/v1/radar-records")
                    .queryParam("radarType", "A")
                    .queryParam("size", 2)
                    .queryParam("anchorId", anchorId)
                    .queryParam("anchorValue", anchorValue)
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items.length()").isEqualTo(1)
            .jsonPath("$.items[0].actionTime").isEqualTo("2026-11-12T10:00:00Z")
            .jsonPath("$.hasNext").isEqualTo(false)
            .jsonPath("$.nextAnchorId").doesNotExist()
    }

    @Test
    fun `multiple values for the same param are combined with IN`() {
        seed("A", "B", "C")
        client.get().uri("/api/v1/radar-records?radarType=A&radarType=C").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.items.length()").isEqualTo(2)
    }

    @Test
    fun `detail returns decoded fields without rawMessage`() {
        val saved = seed("A").single()
        client.get().uri("/api/v1/radar-records/${saved.id!!.toHexString()}").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.message.radarType").isEqualTo("A")
            .jsonPath("$.message.rawMessage").doesNotExist()
            .jsonPath("$.rawMessage").doesNotExist()
            .jsonPath("$.fields[0].label").isEqualTo("payloadSize")
            .jsonPath("$.fields[0].value").isEqualTo("4")
            .jsonPath("$.fields[0].unit").isEqualTo("byte")
    }

    @Test
    fun `invalid requests return 400 and unknown id returns 404`() {
        fun get(uri: String) = client.get().uri(uri).exchange().expectStatus()

        get("/api/v1/radar-records/not-an-id").isBadRequest
        get("/api/v1/radar-records/${ObjectId().toHexString()}").isNotFound
        get("/api/v1/radar-records?size=0").isBadRequest
        get("/api/v1/radar-records?size=201").isBadRequest
        get("/api/v1/radar-records?anchorId=${ObjectId().toHexString()}").isBadRequest
        get("/api/v1/radar-records?actionTimeFrom=yesterday").isBadRequest
        get("/api/v1/radar-records?direction=UP").isBadRequest
    }
}
