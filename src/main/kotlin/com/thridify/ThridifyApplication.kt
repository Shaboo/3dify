package com.thridify

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ThridifyApplication

fun main(args: Array<String>) {
    runApplication<ThridifyApplication>(*args)
}
