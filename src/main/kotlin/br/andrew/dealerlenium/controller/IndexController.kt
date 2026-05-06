package br.andrew.dealerlenium.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
class IndexController {
    @GetMapping("/")
    fun index(): Map<String, String> {
        return mapOf(
            "service" to "dealerlenium",
            "status" to "ok",
            "timestamp" to OffsetDateTime.now().toString(),
        )
    }

    @GetMapping("/health")
    fun health(): Map<String, String> {
        return mapOf(
            "status" to "UP",
            "timestamp" to OffsetDateTime.now().toString(),
        )
    }
}
