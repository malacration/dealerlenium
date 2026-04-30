package br.andrew.dealerlenium

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class DealerleniumApplication

fun main(args: Array<String>) {
    runApplication<DealerleniumApplication>(*args)
}
