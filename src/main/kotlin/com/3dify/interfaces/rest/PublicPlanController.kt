package com.omni3d.interfaces.rest

import com.omni3d.application.service.PlanService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/public/plans")
class PublicPlanController(
    private val planService: PlanService
) {

    /**
     * Returns all active plans ordered by sort_order.
     * Used by the landing page to render dynamic pricing cards — no auth required.
     */
    @GetMapping
    fun listActivePlans() = planService.listAllActive()
}
