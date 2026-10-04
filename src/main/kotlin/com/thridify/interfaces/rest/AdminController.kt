package com.thridify.interfaces.rest

import com.thridify.application.service.plan.createplan.CreatePlanApplicationService
import com.thridify.application.service.plan.createplan.CreatePlanCommand
import com.thridify.application.service.plan.deactivateplan.DeactivatePlanApplicationService
import com.thridify.application.service.plan.deactivateplan.DeactivatePlanCommand
import com.thridify.application.service.plan.listplans.ListPlansApplicationService
import com.thridify.application.service.plan.updateplan.UpdatePlanApplicationService
import com.thridify.application.service.plan.updateplan.UpdatePlanCommand
import com.thridify.interfaces.rest.dto.CreatePlanRequest
import com.thridify.interfaces.rest.dto.UpdatePlanRequest
import com.thridify.interfaces.rest.dto.toResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/admin/plans")
class AdminController(
    private val listPlans: ListPlansApplicationService,
    private val createPlan: CreatePlanApplicationService,
    private val updatePlan: UpdatePlanApplicationService,
    private val deactivatePlan: DeactivatePlanApplicationService,
) {
    @GetMapping
    fun listAllPlans() = listPlans.execute().map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPlan(@RequestBody request: CreatePlanRequest) = createPlan.execute(
        CreatePlanCommand(
            request.name,
            request.displayName,
            request.description,
            request.rateLimitRpm,
            request.monthlyQuota,
            request.priceCents,
            request.currency,
            request.sortOrder,
        ),
    ).toResponse()

    @PutMapping("/{id}")
    fun updatePlan(@PathVariable id: UUID, @RequestBody request: UpdatePlanRequest) = updatePlan.execute(
        UpdatePlanCommand(
            id,
            request.displayName,
            request.description,
            request.priceCents,
            request.rateLimitRpm,
            request.monthlyQuota,
            request.sortOrder,
        ),
    ).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivatePlan(@PathVariable id: UUID) = deactivatePlan.execute(DeactivatePlanCommand(id))
}
