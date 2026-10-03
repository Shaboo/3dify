import re

for filename, imports in [
    ("src/main/kotlin/com/omni3d/interfaces/messaging/TaskWorker.kt", "import com.omni3d.infrastructure.messaging.TaskProducer\nimport com.omni3d.domain.TaskMessage\n"),
    ("src/main/kotlin/com/omni3d/interfaces/rest/filter/JwtAuthFilter.kt", "import com.omni3d.infrastructure.security.JwtService\n")
]:
    with open(filename, 'r') as f:
        content = f.read()
    
    # remove all occurrences of the duplicated imports
    content = content.replace("import com.omni3d.infrastructure.messaging.TaskProducer\n", "")
    content = content.replace("import com.omni3d.domain.TaskMessage\n", "")
    content = content.replace("import com.omni3d.infrastructure.security.JwtService\n", "")
    
    # add them exactly once after the package declaration
    content = re.sub(r'^(package [^\n]+)', r'\1\n\n' + imports, content, count=1, flags=re.MULTILINE)
    
    with open(filename, 'w') as f:
        f.write(content)
