package com.thridify.application.service.subscription.portal

import java.util.UUID

data class CreateBillingPortalCommand(val userId: UUID, val returnUrl: String)
