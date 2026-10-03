import os
import re

FIXES = {
    "com.omni3d.exception.": "com.omni3d.shared.exception.",
    "com.omni3d.domain.": "com.omni3d.domain.",
    "com.omni3d.model.dto.": "com.omni3d.interfaces.rest.dto.",
    "com.omni3d.security.": "com.omni3d.infrastructure.security.",
    "com.omni3d.repository.": "com.omni3d.infrastructure.persistence.",
    "com.omni3d.service.": "com.omni3d.application.service.",
    "com.omni3d.messaging.": "com.omni3d.infrastructure.messaging.",
    "com.omni3d.metrics.": "com.omni3d.shared.metrics.",
    "com.omni3d.config.": "com.omni3d.infrastructure.config.",
}

# Some files might have moved differently, let's just do bulk replace
for base_dir in ["src/main/kotlin", "src/test/kotlin"]:
    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if f.endswith(".kt"):
                path = os.path.join(root, f)
                with open(path, "r") as file:
                    content = file.read()
                
                for old, new in FIXES.items():
                    content = content.replace(old, new)
                    
                # Fix specific class issues:
                content = content.replace("com.omni3d.application.service.RunPodClient", "com.omni3d.infrastructure.provider.runpod.RunPodClient")
                content = content.replace("com.omni3d.application.service.StorageService", "com.omni3d.infrastructure.storage.StorageService")
                content = content.replace("com.omni3d.interfaces.rest.StripeWebhookController", "com.omni3d.interfaces.webhook.StripeWebhookController")
                content = content.replace("com.omni3d.interfaces.rest.ProviderWebhookController", "com.omni3d.interfaces.webhook.ProviderWebhookController")
                content = content.replace("com.omni3d.infrastructure.security.ApiKeyAuthFilter", "com.omni3d.interfaces.rest.filter.ApiKeyAuthFilter")
                content = content.replace("com.omni3d.infrastructure.security.JwtAuthFilter", "com.omni3d.interfaces.rest.filter.JwtAuthFilter")
                
                with open(path, "w") as file:
                    file.write(content)
