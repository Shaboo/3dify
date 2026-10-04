package com.omni3d

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class `3difyApplication`

fun main(args: Array<String>) {
    runApplication<Omni3dApplication>(*args)
}
