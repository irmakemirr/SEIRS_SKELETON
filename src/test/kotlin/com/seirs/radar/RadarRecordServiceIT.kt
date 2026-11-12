package com.seirs.radar

import com.seirs.radar.application.AnchorPageRequest
import com.seirs.radar.application.NewRadarRecord
import com.seirs.radar.application.RadarRecordNotFoundException
import com.seirs.radar.application.RadarRecordService
import com.seirs.radar.data.Anchor
import com.seirs.radar.data.Direction
import com.seirs.radar.data.RADAR_RECORDS_COLLECTION
import com.seirs.radar.data.RadarRecordCriteria
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.bson.types.Binary
import org.bson.types.ObjectId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestSupportConfig::class)
class RadarRecordServiceIT {

    @Autowired lateinit var service: RadarRecordService
    @Autowired lateinit var template: ReactiveMongoTemplate

    private val base: Instant = Instant.parse("2026-11-12T10:00:00Z")

    @BeforeEach
    fun clean(): Unit = runBlocking {
        template.remove(Query(), RADAR_RECORDS_COLLECTION).awaitSingle()
    }

    private suspend fun search(criteria: RadarRecordCriteria, anchor: Anchor? = null, size: Int = 50) =
        service.search(criteria, AnchorPageRequest(anchor, size))

    @Test
    fun `record stores rawMessage as BinData, truncates actionTime to ms and sets lastUpdated`(): Unit = runBlocking {
        val raw = byteArrayOf(0x00, 0x10, -0x01)
        val saved = service.record(NewRadarRecord(radarMessage(raw = raw), base.plusNanos(123_456_789)))

        val doc = template.findById(saved.id!!, Document::class.java, RADAR_RECORDS_COLLECTION).awaitSingleOrNull()
        assertNotNull(doc)
        val message = doc.get("message", Document::class.java)
        val binary = assertIs<Binary>(message["rawMessage"])
        assertTrue(raw.contentEquals(binary.data))
        assertEquals(base.plusMillis(123), doc.getDate("actionTime").toInstant())
        assertNotNull(doc.getDate("lastUpdated"))
        assertEquals("RECEIVED", message.getString("direction"))
    }

    @Test
    fun `anchor pagination walks all records without duplicates even when actionTime ties`(): Unit = runBlocking {
        // 7 kayıt, 3'ü aynı actionTime'a sahip
        listOf(0L, 1, 1, 1, 2, 3, 4).forEach {
            service.record(NewRadarRecord(radarMessage(), base.plusSeconds(it)))
        }

        val seen = mutableListOf<ObjectId>()
        var anchor: Anchor? = null
        var pages = 0
        do {
            val slice = search(RadarRecordCriteria(), anchor, size = 2)
            seen += slice.items.map { it.id }
            pages++
            anchor = if (slice.hasNext) Anchor(slice.nextAnchorId!!, slice.nextAnchorValue!!) else null
        } while (anchor != null)

        assertEquals(4, pages)
        assertEquals(7, seen.toSet().size)

        val all = search(RadarRecordCriteria())
        assertFalse(all.hasNext)
        assertEquals(seen, all.items.map { it.id })
        val times = all.items.map { it.actionTime }
        assertEquals(times.sortedDescending(), times)
    }

    @Test
    fun `filters combine IN per field with AND across fields and time range`(): Unit = runBlocking {
        service.record(NewRadarRecord(radarMessage(radarType = "A", radarId = "R-1"), base))
        service.record(NewRadarRecord(radarMessage(radarType = "B", radarId = "R-1"), base.plusSeconds(1)))
        service.record(NewRadarRecord(radarMessage(radarType = "C", radarId = "R-1"), base.plusSeconds(2)))
        service.record(
            NewRadarRecord(radarMessage(radarType = "A", radarId = "R-2", direction = Direction.SENT), base.plusSeconds(3)),
        )

        suspend fun find(c: RadarRecordCriteria) =
            search(c).items.map { it.message.radarType to it.message.radarId }

        assertEquals(listOf("A" to "R-2", "B" to "R-1", "A" to "R-1"), find(RadarRecordCriteria(radarTypes = listOf("A", "B"))))
        assertEquals(
            listOf("B" to "R-1", "A" to "R-1"),
            find(RadarRecordCriteria(radarTypes = listOf("A", "B"), radarIds = listOf("R-1"))),
        )
        assertEquals(listOf("A" to "R-2"), find(RadarRecordCriteria(directions = listOf(Direction.SENT))))
        assertEquals(
            listOf("C" to "R-1", "B" to "R-1"),
            find(RadarRecordCriteria(actionTimeFrom = base.plusSeconds(1), actionTimeTo = base.plusSeconds(3))),
        )
    }

    @Test
    fun `getDetail returns decoder output and throws for unknown id`(): Unit = runBlocking {
        val saved = service.record(NewRadarRecord(radarMessage(messageCode = 42), base))
        val detail = service.getDetail(saved.id!!)
        assertEquals(listOf("4", "42"), detail.decodedFields.map { it.value })

        assertFailsWith<RadarRecordNotFoundException> { service.getDetail(ObjectId()) }
    }
}
