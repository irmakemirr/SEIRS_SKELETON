package com.seirs.radar.presentation

import com.seirs.radar.application.InvalidRadarConfigException
import com.seirs.radar.application.RadarAlreadyExistsException
import com.seirs.radar.application.RadarNotFoundException
import com.seirs.radar.application.RadarRecordNotFoundException
import com.seirs.radar.application.RadarStateConflictException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

class InvalidRequestException(message: String) : RuntimeException(message)

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvalidRequestException::class, InvalidRadarConfigException::class)
    fun handleInvalid(ex: RuntimeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request")

    @ExceptionHandler(RadarRecordNotFoundException::class, RadarNotFoundException::class)
    fun handleNotFound(ex: RuntimeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found")

    @ExceptionHandler(RadarAlreadyExistsException::class, RadarStateConflictException::class)
    fun handleConflict(ex: RuntimeException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Conflict")
}
