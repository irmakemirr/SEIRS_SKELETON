package com.seirs.radar

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RadarRecordsApplication

fun main(args: Array<String>) {
    runApplication<RadarRecordsApplication>(*args)
}
