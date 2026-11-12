package com.seirs.radar.data

import org.bson.types.ObjectId
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface RadarRecordRepository : CoroutineCrudRepository<RadarRecordDocument, ObjectId>
