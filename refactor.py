import os
import re
import shutil

SRC_DIR = "src/main/kotlin"
TEST_DIR = "src/test/kotlin"

# Mapping old package/class paths to new package names
# We use com.omni3d instead of com.3dify because 3dify starts with a number and is illegal in Java packages.

MAPPINGS = {
    "config": "com.omni3d.infrastructure.config",
    "controller": "com.omni3d.interfaces.rest",
    "model/dto": "com.omni3d.interfaces.rest.dto",
    "domain": "com.omni3d.domain",
    "exception": "com.omni3d.shared.exception",
    "messaging": "com.omni3d.infrastructure.messaging",
    "metrics": "com.omni3d.shared.metrics",
    "repository": "com.omni3d.infrastructure.persistence",
    "security": "com.omni3d.infrastructure.security",
    "service": "com.omni3d.application.service",
}

# Special files
SPECIAL_FILES = {
    "Omni3dApplication.kt": "com.omni3d.infrastructure",
    "ApiKeyAuthFilter.kt": "com.omni3d.interfaces.rest.filter",
    "JwtAuthFilter.kt": "com.omni3d.interfaces.rest.filter",
    "RunPodClient.kt": "com.omni3d.infrastructure.provider.runpod",
    "StorageService.kt": "com.omni3d.infrastructure.storage",
    "StripeConfig.kt": "com.omni3d.infrastructure.billing",
    "StripeWebhookController.kt": "com.omni3d.interfaces.webhook",
    "ProviderWebhookController.kt": "com.omni3d.interfaces.webhook",
    "WebhookController.kt": "com.omni3d.interfaces.rest", 
    "TaskWorker.kt": "com.omni3d.interfaces.messaging", 
}

def get_new_package(filename, filepath):
    if filename in SPECIAL_FILES:
        return SPECIAL_FILES[filename]
        
    for old_path, new_pkg in MAPPINGS.items():
        if f"/{old_path}/" in filepath:
            return new_pkg
            
    if filename.endswith("Exception.kt") or filename == "GlobalExceptionHandler.kt":
        return "com.omni3d.shared.exception"
        
    return "com.omni3d"

def refactor_dir(base_dir):
    moves = []
    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if f.endswith(".kt"):
                filepath = os.path.join(root, f)
                new_pkg = get_new_package(f, filepath)
                new_dir = os.path.join(base_dir, new_pkg.replace(".", "/"))
                new_path = os.path.join(new_dir, f)
                
                if "test" in base_dir:
                    new_pkg = get_new_package(f.replace("Test", ""), filepath.replace("Test", ""))
                    new_dir = os.path.join(base_dir, new_pkg.replace(".", "/"))
                    new_path = os.path.join(new_dir, f)
                
                moves.append((filepath, new_path, new_pkg))

    class_to_new_pkg = {}
    
    for old_path, new_path, new_pkg in moves:
        filename = os.path.basename(old_path)
        class_name = filename.replace(".kt", "")
        with open(old_path, "r") as f:
            content = f.read()
        
        match = re.search(r'^package\s+([a-zA-Z0-9_.]+)', content, re.MULTILINE)
        if match:
            old_pkg = match.group(1)
            class_to_new_pkg[f"{old_pkg}.{class_name}"] = f"{new_pkg}.{class_name}"
            if class_name == "Dtos":
                class_to_new_pkg[f"{old_pkg}.LoginRequest"] = f"{new_pkg}.LoginRequest"
                class_to_new_pkg[f"{old_pkg}.RegisterRequest"] = f"{new_pkg}.RegisterRequest"
                class_to_new_pkg[f"{old_pkg}.AuthResponse"] = f"{new_pkg}.AuthResponse"
                class_to_new_pkg[f"{old_pkg}.SubscriptionStatusResponse"] = f"{new_pkg}.SubscriptionStatusResponse"
                class_to_new_pkg[f"{old_pkg}.CheckoutResponse"] = f"{new_pkg}.CheckoutResponse"
                class_to_new_pkg[f"{old_pkg}.PortalResponse"] = f"{new_pkg}.PortalResponse"
                class_to_new_pkg[f"{old_pkg}.PlanDto"] = f"{new_pkg}.PlanDto"
                class_to_new_pkg[f"{old_pkg}.ApiKeyResponse"] = f"{new_pkg}.ApiKeyResponse"
                class_to_new_pkg[f"{old_pkg}.ApiKeyCreatedResponse"] = f"{new_pkg}.ApiKeyCreatedResponse"
                class_to_new_pkg[f"{old_pkg}.JobResponse"] = f"{new_pkg}.JobResponse"
                class_to_new_pkg[f"{old_pkg}.JobHistoryResponse"] = f"{new_pkg}.JobHistoryResponse"
            if class_name == "entities":
                class_to_new_pkg[f"{old_pkg}.UserEntity"] = f"{new_pkg}.UserEntity"
                class_to_new_pkg[f"{old_pkg}.PlanEntity"] = f"{new_pkg}.PlanEntity"
                class_to_new_pkg[f"{old_pkg}.ApiKeyAuthEntity"] = f"{new_pkg}.ApiKeyAuthEntity"
                class_to_new_pkg[f"{old_pkg}.ApiKeyWithPlanEntity"] = f"{new_pkg}.ApiKeyWithPlanEntity"
                class_to_new_pkg[f"{old_pkg}.JobEntity"] = f"{new_pkg}.JobEntity"
                class_to_new_pkg[f"{old_pkg}.JobStatus"] = f"{new_pkg}.JobStatus"
                class_to_new_pkg[f"{old_pkg}.WebhookEntity"] = f"{new_pkg}.WebhookEntity"
                class_to_new_pkg[f"{old_pkg}.SubscriptionWithPlanEntity"] = f"{new_pkg}.SubscriptionWithPlanEntity"
                class_to_new_pkg[f"{old_pkg}.TaskMessage"] = f"{new_pkg}.TaskMessage"

    for old_path, new_path, new_pkg in moves:
        os.makedirs(os.path.dirname(new_path), exist_ok=True)
        shutil.move(old_path, new_path)

    return moves, class_to_new_pkg

moves_main, map_main = refactor_dir(SRC_DIR)
moves_test, map_test = refactor_dir(TEST_DIR)

class_map = {**map_main, **map_test}
class_map["com.omni3d.api.Omni3dApplication"] = "com.omni3d.infrastructure.Omni3dApplication"
class_map["com.omni3d.api.IntegrationTestBase"] = "com.omni3d.IntegrationTestBase"
class_map["com.omni3d.api.TestBeanConfig"] = "com.omni3d.TestBeanConfig"
class_map["com.omni3d.api.TestHelpers"] = "com.omni3d.TestHelpers"
class_map["com.omni3d.api.TestHelpersKt"] = "com.omni3d.TestHelpersKt"

print(f"Moved {len(moves_main) + len(moves_test)} files.")

def update_contents(base_dir):
    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if f.endswith(".kt"):
                path = os.path.join(root, f)
                with open(path, "r") as file:
                    content = file.read()
                
                rel_path = os.path.relpath(path, base_dir)
                new_pkg = os.path.dirname(rel_path).replace(os.sep, ".")
                content = re.sub(r'^package\s+.*', f"package {new_pkg}", content, flags=re.MULTILINE)
                
                content = content.replace("com.omni3d.api.*", "com.omni3d.*")
                
                # Replace fully qualified imports longest first to avoid partial replacements
                for old_class in sorted(class_map.keys(), key=len, reverse=True):
                    new_class = class_map[old_class]
                    content = content.replace(f"import {old_class}", f"import {new_class}")
                
                content = content.replace("com.omni3d.api.", "com.omni3d.")
                
                with open(path, "w") as file:
                    file.write(content)

update_contents(SRC_DIR)
update_contents(TEST_DIR)

for base_dir in [SRC_DIR, TEST_DIR]:
    for root, dirs, files in os.walk(base_dir, topdown=False):
        for name in dirs:
            try:
                os.rmdir(os.path.join(root, name))
            except OSError:
                pass
