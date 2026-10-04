package com.thridify.interfaces.rest

import com.thridify.application.service.generation.submit.GenerateModelApplicationService
import com.thridify.application.service.generation.submit.GenerateModelCommand
import com.thridify.application.service.generation.submit.GenerationImage
import com.thridify.application.service.job.getjob.GetJobApplicationService
import com.thridify.application.service.job.getjob.GetJobQuery
import com.thridify.application.service.job.history.GetJobHistoryApplicationService
import com.thridify.application.service.job.history.GetJobHistoryQuery
import com.thridify.application.service.job.listapikeyjobs.ListApiKeyJobsApplicationService
import com.thridify.application.service.job.listapikeyjobs.ListApiKeyJobsQuery
import com.thridify.interfaces.rest.dto.toResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class GenerateController(
    private val generate: GenerateModelApplicationService,
    private val listJobs: ListApiKeyJobsApplicationService,
    private val getJob: GetJobApplicationService,
    private val history: GetJobHistoryApplicationService,
) {
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun generate(
        authentication: Authentication,
        @RequestPart("image1") image1: MultipartFile,
        @RequestPart("image2") image2: MultipartFile,
    ) = generate.execute(GenerateModelCommand(UUID.fromString(authentication.principal as String), image1.toInput(), image2.toInput())).toResponse()

    @GetMapping("/jobs")
    fun listJobs(authentication: Authentication) = listJobs.execute(ListApiKeyJobsQuery(UUID.fromString(authentication.principal as String))).map { it.toResponse() }

    @GetMapping("/jobs/{jobId}")
    fun getJob(@PathVariable jobId: UUID) = getJob.execute(GetJobQuery(jobId)).toResponse()

    @GetMapping("/jobs/{jobId}/history")
    fun getJobHistory(@PathVariable jobId: UUID) = history.execute(GetJobHistoryQuery(jobId)).map { it.toResponse() }
    private fun MultipartFile.toInput() = GenerationImage(bytes, originalFilename, contentType ?: "image/png")
}
