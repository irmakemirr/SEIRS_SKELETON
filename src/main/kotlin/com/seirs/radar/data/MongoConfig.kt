package com.seirs.radar.data

import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing

@Configuration(proxyBeanMethods = false)
@EnableReactiveMongoAuditing
class MongoConfig
