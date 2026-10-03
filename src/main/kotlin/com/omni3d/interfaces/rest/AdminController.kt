package com.omni3d.interfaces.rest

import com.omni3d.interfaces.rest.dto.CreatePlanRequest
import com.omni3d.interfaces.rest.dto.UpdatePlanRequest
import com.omni3d.application.service.PlanService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/admin/plans")
class AdminController(
    private val planService: PlanService
) {

    @GetMapping
    fun listAllPlans() = planService.listAll()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPlan(@RequestBody request: CreatePlanRequest) =
        planService.createPlan(
            name = request.name,
            displayName = request.displayName,
            description = request.description,
            rateLimitRpm = request.rateLimitRpm,
            monthlyQuota = request.monthlyQuota,
            priceCents = request.priceCents,
            currency = request.currency,
            sortOrder = request.sortOrder
        )

    @PutMapping("/{id}")
    fun updatePlan(
        @PathVariable id: UUID,
        @RequestBody request: UpdatePlanRequest
    ) = planService.updatePlan(
        id = id,
        displayName = request.displayName,
        description = request.description,
        priceCents = request.priceCents,
        rateLimitRpm = request.rateLimitRpm,
        monthlyQuota = request.monthlyQuota,
        sortOrder = request.sortOrder
    )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivatePlan(@PathVariable id: UUID) =
        planService.deactivatePlan(id)
}
