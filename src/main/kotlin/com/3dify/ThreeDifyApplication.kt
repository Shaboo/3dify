package com.`3dify`

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ThreeDifyApplication

fun main(args: Array<String>) {
    runApplication<ThreeDifyApplication>(*args)
}
