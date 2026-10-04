package com.thridify.interfaces.rest

import com.thridify.application.service.plan.listactive.ListActivePlansApplicationService
import com.thridify.interfaces.rest.dto.toResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/public/plans")
class PublicPlanController(
    private val listActivePlans: ListActivePlansApplicationService,
) {

    /**
     * Returns all active plans ordered by sort_order.
     * Used by the landing page to render dynamic pricing cards — no auth required.
     */
    @GetMapping
    fun listActivePlans() = listActivePlans.execute().map { it.toResponse() }
}
