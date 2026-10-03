import os

with open('src/main/kotlin/com/omni3d/interfaces/messaging/TaskWorker.kt', 'r') as f:
    content = f.read()
content = content.replace("import com.omni3d.infrastructure.messaging.TaskWorker", "import com.omni3d.infrastructure.messaging.TaskProducer\nimport com.omni3d.domain.TaskMessage")
# TaskMessage was mapped to domain.entities but wait, let's just see.
with open('src/main/kotlin/com/omni3d/interfaces/messaging/TaskWorker.kt', 'w') as f:
    f.write(content)

with open('src/main/kotlin/com/omni3d/interfaces/rest/filter/JwtAuthFilter.kt', 'r') as f:
    content = f.read()
content = content.replace("import com.omni3d.interfaces.rest.filter.JwtService", "import com.omni3d.infrastructure.security.JwtService")
with open('src/main/kotlin/com/omni3d/interfaces/rest/filter/JwtAuthFilter.kt', 'w') as f:
    f.write(content)
