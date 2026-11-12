package com.seirs.radar

import com.seirs.radar.application.AnchorPageRequest
import com.seirs.radar.application.RadarControlService
import com.seirs.radar.application.RadarRecordService
import com.seirs.radar.data.Direction
import com.seirs.radar.data.RADAR_RECORDS_COLLECTION
import com.seirs.radar.data.RadarRecordCriteria
import com.seirs.radar.data.RadarRecordSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSupportConfig::class)
class RadarSimulationIT {

    @LocalServerPort var port: Int = 0
    @Autowired lateinit var control: RadarControlService
    @Autowired lateinit var records: RadarRecordService
    @Autowired lateinit var template: ReactiveMongoTemplate

    private val client by lazy { WebTestClient.bindToServer().baseUrl("http://localhost:$port").build() }

    @BeforeEach
    fun clean(): Unit = runBlocking {
        template.remove(Query(), RADAR_RECORDS_COLLECTION).awaitSingle()
    }

    /** Simülatör paylaşılan context'te yaşar; diğer test sınıflarını kirletmemek için radarları kapat. */
    @AfterEach
    fun stopRadars(): Unit = runBlocking {
        control.list().forEach { control.remove(it.config.radarId) }
        template.remove(Query(), RADAR_RECORDS_COLLECTION).awaitSingle()
    }

    private fun json(body: String) = client.post().uri("/api/v1/radars")
        .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange()

    private suspend fun find(radarId: String, direction: Direction? = null): List<RadarRecordSummary> =
        records.search(
            RadarRecordCriteria(radarIds = listOf(radarId), directions = listOfNotNull(direction)),
            AnchorPageRequest(null, 200),
        ).items

    @Test
    fun `radar lifecycle produces SENT commands and RECEIVED plots`(): Unit = runBlocking {
        json("""{"radarId":"SIM-1","radarType":"SEARCH","rotationPeriodMs":100,"minPlots":2,"maxPlots":2}""")
            .expectStatus().isCreated
            .expectBody().jsonPath("$.radarId").isEqualTo("SIM-1")

        client.get().uri("/api/v1/radars").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$[0].radarId").isEqualTo("SIM-1")

        // en az 3 tur * 2 plot
        withTimeout(10_000) {
            while (find("SIM-1", Direction.RECEIVED).size < 6) delay(100)
        }
        val plots = find("SIM-1", Direction.RECEIVED)
        assertTrue(plots.all { it.message.messageType == "PLOT" && it.message.radarType == "SEARCH" })

        client.put().uri("/api/v1/radars/SIM-1").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"rotationPeriodMs":200,"minPlots":0,"maxPlots":1}""")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.rotationPeriodMs").isEqualTo(200)

        client.delete().uri("/api/v1/radars/SIM-1").exchange().expectStatus().isNoContent

        val sent = find("SIM-1", Direction.SENT).map { it.message.messageName }
        assertEquals(listOf("RemoveRadar", "UpdateRadarConfig", "AddRadar"), sent)

        // Silindikten sonra yeni plot üretilmez
        val countAfterRemove = find("SIM-1", Direction.RECEIVED).size
        delay(400)
        assertEquals(countAfterRemove, find("SIM-1", Direction.RECEIVED).size)
    }

    @Test
    fun `stop halts plots, start resumes them, both are recorded as SENT`(): Unit = runBlocking {
        json("""{"radarId":"SIM-3","radarType":"SEARCH","rotationPeriodMs":100,"minPlots":1,"maxPlots":1}""")
            .expectStatus().isCreated
            .expectBody().jsonPath("$.running").isEqualTo(true)
        withTimeout(10_000) { while (find("SIM-3", Direction.RECEIVED).size < 2) delay(50) }

        client.post().uri("/api/v1/radars/SIM-3/stop").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.running").isEqualTo(false)
        client.post().uri("/api/v1/radars/SIM-3/stop").exchange().expectStatus().isEqualTo(409)

        delay(150) // uçuşta olan kayıt yazılsın
        val stoppedCount = find("SIM-3", Direction.RECEIVED).size
        delay(400)
        assertEquals(stoppedCount, find("SIM-3", Direction.RECEIVED).size)

        client.post().uri("/api/v1/radars/SIM-3/start").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.running").isEqualTo(true)
        client.post().uri("/api/v1/radars/SIM-3/start").exchange().expectStatus().isEqualTo(409)
        withTimeout(10_000) { while (find("SIM-3", Direction.RECEIVED).size <= stoppedCount) delay(50) }

        assertEquals(
            listOf("StartRadar", "StopRadar", "AddRadar"),
            find("SIM-3", Direction.SENT).map { it.message.messageName },
        )
        client.post().uri("/api/v1/radars/NOPE/start").exchange().expectStatus().isNotFound
    }

    @Test
    fun `invalid, duplicate and unknown radars are rejected`() {
        json("""{"radarId":"SIM-2","radarType":"SEARCH","rotationPeriodMs":50,"minPlots":1,"maxPlots":2}""")
            .expectStatus().isBadRequest
        json("""{"radarId":"SIM-2","radarType":"SEARCH","rotationPeriodMs":1000,"minPlots":5,"maxPlots":2}""")
            .expectStatus().isBadRequest
        json("""{"radarId":"bad id!","radarType":"SEARCH","rotationPeriodMs":1000,"minPlots":1,"maxPlots":2}""")
            .expectStatus().isBadRequest
        json("""{"radarId":"SIM-2"}""").expectStatus().isBadRequest

        json("""{"radarId":"SIM-2","radarType":"SEARCH","rotationPeriodMs":60000,"minPlots":0,"maxPlots":0}""")
            .expectStatus().isCreated
        json("""{"radarId":"SIM-2","radarType":"SEARCH","rotationPeriodMs":60000,"minPlots":0,"maxPlots":0}""")
            .expectStatus().isEqualTo(409)

        client.delete().uri("/api/v1/radars/NOPE").exchange().expectStatus().isNotFound
        client.put().uri("/api/v1/radars/NOPE").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"rotationPeriodMs":1000,"minPlots":0,"maxPlots":1}""")
            .exchange().expectStatus().isNotFound
    }
}
