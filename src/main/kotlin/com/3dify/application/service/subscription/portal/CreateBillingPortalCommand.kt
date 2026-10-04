package com.`3dify`.application.service.subscription.portal

import java.util.UUID

data class CreateBillingPortalCommand(val userId: UUID, val returnUrl: String)
