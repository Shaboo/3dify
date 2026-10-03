package com.omni3d.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class Omni3dApplication

fun main(args: Array<String>) {
    runApplication<Omni3dApplication>(*args)
}
