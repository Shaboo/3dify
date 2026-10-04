#!/usr/bin/env python3
"""
DSA refactoring inventory: scans Kotlin/Java main sources and classifies every file
into a *suggested* DSA layer, flagging classes that mix responsibilities.

Heuristic by design. It gives you a complete, repeatable starting inventory, not a verdict.
Always confirm a classification by reading the class before moving it.

Usage:
  python3 inventory.py [repo_root] [--format md|csv|json] [--out FILE] [--include-generated]

Examples:
  python3 inventory.py . --out docs/dsa-migration/inventory.md
  python3 inventory.py ~/code/my-service --format csv --out /tmp/inventory.csv
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import os
import re
import sys
from collections import Counter
from dataclasses import asdict, dataclass, field

SOURCE_EXTENSIONS = (".kt", ".java")
SKIP_DIR_NAMES = {"build", "out", "target", ".gradle", ".git", ".idea", "node_modules", "generated", "generated-sources"}

ENTRYPOINT_ANNOTATIONS = {
    "RestController", "Controller", "KafkaListener", "RabbitListener", "RabbitHandler", "KafkaHandler",
    "Scheduled", "GrpcService", "MessageMapping", "JmsListener", "SqsListener", "StreamListener",
    "QueryMapping", "MutationMapping", "SchemaMapping", "DgsComponent",
}
ANNOTATIONS_OF_INTEREST = ENTRYPOINT_ANNOTATIONS | {
    "Service", "Component", "Repository", "Configuration", "Entity", "Table", "Embeddable", "Transactional",
    "ConfigurationProperties", "Value", "JsonProperty", "JsonTypeInfo", "XmlElement", "XmlRootElement",
    "DisallowConcurrentExecution", "SchedulerLock", "ControllerAdvice", "RestControllerAdvice", "Mapper",
    "HttpExchange", "FeignClient", "Serializable", "Masked", "SpringBootApplication",
}
TECH_IMPORT_PATTERNS = {
    "jooq": r"^import\s+org\.jooq\b",
    "jpa": r"^import\s+(jakarta|javax)\.persistence\b|^import\s+org\.springframework\.data\.(jpa|repository)\b",
    "jdbc": r"^import\s+org\.springframework\.jdbc\b|^import\s+java\.sql\b",
    "jackson": r"^import\s+com\.fasterxml\.jackson\b",
    "jaxb": r"^import\s+(jakarta|javax)\.xml\.bind\b",
    "kafka": r"^import\s+org\.(springframework\.kafka|apache\.kafka)\b",
    "rabbit": r"^import\s+(org\.springframework\.amqp|com\.rabbitmq)\b",
    "http-client": r"^import\s+.*\b(RestTemplate|WebClient|RestClient|HttpExchange|FeignClient|retrofit2|okhttp3|HttpClient)\b",
    "spring-web": r"^import\s+org\.springframework\.web\b|^import\s+org\.springframework\.http\b",
    "aws": r"^import\s+(software\.amazon\.awssdk|com\.amazonaws)\b",
    "quartz": r"^import\s+org\.quartz\b",
    "spring-tx": r"^import\s+org\.springframework\.transaction\b",
    "micrometer": r"^import\s+io\.micrometer\b",
    "avro": r"^import\s+(org\.apache\.avro|com\.github\.avrokotlin)\b",
}
PERSISTENCE_TECH = {"jooq", "jpa", "jdbc"}
INTEGRATION_TECH = {"kafka", "rabbit", "http-client", "aws"}
SERIALIZATION_TECH = {"jackson", "jaxb", "avro"}
LAYER_SEGMENTS = {"interfaces": "interfaces", "application": "application", "domain": "domain",
                  "infrastructure": "infrastructure", "shared": "shared", "common": "shared"}
DTO_SUFFIXES = ("Request", "Response", "RequestObject", "ResponseObject", "Dto", "DTO", "Payload", "Body", "View", "Resource")
INJECT_SUFFIXES = ("ApplicationService", "Service", "Repository", "Dao", "Publisher", "Notifier", "Client",
                   "Template", "DSLContext", "DslContext", "EntityManager", "Gateway", "Port", "Adapter",
                   "Factory", "Mapper", "Handler", "UseCase", "Facade", "Manager", "Provider")

TYPE_DECL = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:public\s+|private\s+|internal\s+|protected\s+|final\s+|open\s+|abstract\s+|sealed\s+|data\s+|enum\s+|value\s+|inner\s+|static\s+|annotation\s+|fun\s+)*"
    r"(class|interface|object|record|enum)\s+([A-Z]\w*)", re.MULTILINE)
ANNOTATION = re.compile(r"@([A-Z]\w*)")
KOTLIN_PARAM = re.compile(r"(?:val|var)\s+\w+\s*:\s*([A-Z][\w.]*)")
JAVA_FIELD = re.compile(r"private\s+(?:final\s+)?([A-Z][\w.]*)(?:<[^;=]*>)?\s+\w+\s*[;=]")


@dataclass
class Entry:
    file: str
    package: str
    primary_type: str
    kind: str
    types_in_file: int
    current_layer: str
    annotations: list[str] = field(default_factory=list)
    tech: list[str] = field(default_factory=list)
    injects: list[str] = field(default_factory=list)
    role: str = ""
    suggested_layer: str = ""
    flags: list[str] = field(default_factory=list)


def find_sources(root: str, include_generated: bool) -> list[str]:
    results = []
    for dirpath, dirnames, filenames in os.walk(root):
        skip = SKIP_DIR_NAMES if not include_generated else SKIP_DIR_NAMES - {"generated", "generated-sources"}
        dirnames[:] = [d for d in dirnames if d not in skip and not d.startswith(".")]
        norm = dirpath.replace(os.sep, "/")
        if "/src/main/" not in norm + "/":
            continue
        for name in filenames:
            if name.endswith(SOURCE_EXTENSIONS):
                results.append(os.path.join(dirpath, name))
    return sorted(results)


def current_layer_of(package: str) -> str:
    for segment in package.split("."):
        if segment in LAYER_SEGMENTS:
            return LAYER_SEGMENTS[segment]
    return "none"


def constructor_injections(text: str, primary: str, is_java: bool) -> list[str]:
    found: list[str] = []
    if is_java:
        found = JAVA_FIELD.findall(text)
    else:
        header = re.search(rf"\b(?:class|object)\s+{re.escape(primary)}\b[^({{]*?\(", text)
        if header:
            start, depth, i = header.end(), 1, header.end()
            while i < len(text) and depth:
                depth += {"(": 1, ")": -1}.get(text[i], 0)
                i += 1
            found = KOTLIN_PARAM.findall(text[start:i])
    simple = {t.split(".")[-1] for t in found}
    return sorted(t for t in simple if t.endswith(INJECT_SUFFIXES))


DSA_LAYERS = ("interfaces", "application", "domain", "infrastructure")


def classify(e: Entry, text: str) -> None:
    """Assign role + suggested layer. 'strong' means the evidence (annotations/imports) is hard to argue with;
    weak (name-based) guesses defer to the class's current DSA layer, so an existing layout isn't second-guessed."""
    ann, tech, name = set(e.annotations), set(e.tech), e.primary_type
    is_data = re.search(rf"\b(data\s+class|record|enum\s+class|enum)\s+{re.escape(name)}\b", text) is not None
    is_entrypoint = bool(ann & ENTRYPOINT_ANNOTATIONS) or re.search(r":\s*(Quartz)?Job\b|implements\s+Job\b|QuartzJobBean", text) is not None
    injects_persistence = [i for i in e.injects if i.endswith(("Dao", "DSLContext", "DslContext", "EntityManager", "Template"))]
    injects_app_services = [i for i in e.injects if i.endswith("ApplicationService") and i != name]
    injects_io_ports = [i for i in e.injects if i.endswith(("Repository", "Dao", "Publisher", "Notifier", "Client", "Gateway"))]
    strong = True

    if "SpringBootApplication" in ann:
        e.role, e.suggested_layer = "composition-root", "root package (no layer)"
        return
    if is_entrypoint:
        e.role, e.suggested_layer = "entry-point", "interfaces"
        if tech & PERSISTENCE_TECH or injects_persistence or injects_io_ports:
            e.flags.append("fat-entry-point: " + ",".join(sorted(set(injects_persistence + injects_io_ports)) or sorted(tech & PERSISTENCE_TECH)))
        if len(injects_app_services) > 1:
            # Normal for a controller with several endpoints; a violation only if ONE handler calls two of them.
            e.flags.append("several-app-services (check no single handler composes them): " + ",".join(injects_app_services))
    elif ann & {"ControllerAdvice", "RestControllerAdvice"}:
        e.role, e.suggested_layer = "http-error-mapping", "interfaces"
    elif ann & {"Entity", "Table", "Embeddable"} and "jpa" in tech:
        e.role, e.suggested_layer = "orm-entity", "infrastructure"
        e.flags.append("split: pure domain model + persistence entity + mapper")
    elif e.kind == "interface" and name.endswith(("Repository", "Publisher", "Notifier", "Client", "Gateway", "Port", "Provider")) \
            and not tech & (PERSISTENCE_TECH | INTEGRATION_TECH):
        # Interfaces internal to infrastructure (e.g. outbox plumbing) are legitimate there.
        e.role, e.suggested_layer, strong = "port", "domain", e.current_layer != "infrastructure"
    elif tech & PERSISTENCE_TECH or name.endswith(("Dao", "RecordMapper")) or "Repository" in ann:
        e.role, e.suggested_layer = "persistence-adapter", "infrastructure"
    elif tech & INTEGRATION_TECH and e.current_layer == "interfaces":
        e.role, e.suggested_layer, strong = "inbound-protocol-support", "interfaces", False
    elif tech & INTEGRATION_TECH and not name.endswith("ApplicationService"):
        e.role, e.suggested_layer = "integration-adapter", "infrastructure"
    elif "Configuration" in ann:
        e.role = "configuration"
        if e.current_layer == "interfaces" and not tech & (PERSISTENCE_TECH | INTEGRATION_TECH | {"aws"}):
            # Trigger/endpoint/security config for inbound adapters (Quartz JobDetail+Trigger, web security) sits with them.
            e.suggested_layer, strong = "interfaces", False
        elif tech & (PERSISTENCE_TECH | INTEGRATION_TECH | {"spring-web", "quartz"}):
            e.suggested_layer = "infrastructure"
        else:
            e.suggested_layer, strong = "infrastructure (tech config) or owning layer", False
    elif name.endswith("ApplicationService") or (
        ann & {"Service", "Component"} and name.endswith(("Service", "UseCase", "Interactor", "Handler", "Facade", "Manager", "Orchestrator"))
        and (injects_io_ports or "Transactional" in ann)
    ):
        e.role, e.suggested_layer = "use-case-orchestration", "application"
        strong = name.endswith("ApplicationService") or "Transactional" in ann or e.current_layer not in DSA_LAYERS
    elif name.endswith(("Command", "Query", "CommandResult", "QueryResult")):
        e.role, e.suggested_layer, strong = "command/query/result", "application", False
    elif "ConfigurationProperties" in ann:
        e.role, e.suggested_layer, strong = "config-properties", "infrastructure (or the layer that owns the setting)", False
    elif name.endswith(DTO_SUFFIXES) or (is_data and tech & SERIALIZATION_TECH):
        e.role, e.suggested_layer, strong = "api-or-message-dto", "interfaces (or infrastructure if outbound payload)", False
    elif ann & {"Service", "Component"}:
        e.role, e.suggested_layer, strong = "domain-service?", "domain", False
    else:
        e.role, e.suggested_layer, strong = "model/value/util", "domain (or shared if purely technical)", False

    # Weak guesses never override an existing DSA placement.
    if not strong and e.current_layer in DSA_LAYERS and not e.suggested_layer.startswith(e.current_layer):
        e.suggested_layer = f"{e.current_layer} (keep; weak signal: {e.suggested_layer.split(' ')[0]})"

    # Rule-violation flags (these hold regardless of the heuristics above).
    if e.current_layer == "domain" and tech - {"micrometer"}:
        e.flags.append("impure-domain: " + ",".join(sorted(tech - {"micrometer"})))
    if e.current_layer == "domain" and ann & {"Transactional", "Value", "ConfigurationProperties"}:
        e.flags.append("spring-behaviour-in-domain: " + ",".join(sorted(ann & {"Transactional", "Value", "ConfigurationProperties"})))
    if e.current_layer == "domain" and e.kind in ("class", "object") and injects_io_ports:
        e.flags.append("domain-service-does-io: " + ",".join(injects_io_ports))
    if e.current_layer == "application" and tech & (PERSISTENCE_TECH | INTEGRATION_TECH):
        e.flags.append("infra-in-application: " + ",".join(sorted(tech & (PERSISTENCE_TECH | INTEGRATION_TECH))))
    if e.role == "use-case-orchestration" and injects_app_services:
        e.flags.append("calls-other-app-service: " + ",".join(injects_app_services))
    if "Transactional" in ann:
        e.flags.append("@Transactional→TransactionProvider")
    if strong and e.current_layer in DSA_LAYERS and not e.suggested_layer.startswith(e.current_layer):
        e.flags.append(f"misplaced? {e.current_layer}→{e.suggested_layer.split(' ')[0]}")
    if e.types_in_file > 4:
        e.flags.append(f"many-types-in-file: {e.types_in_file}")


def analyse(path: str, root: str) -> Entry | None:
    try:
        text = open(path, encoding="utf-8", errors="replace").read()
    except OSError:
        return None
    pkg_match = re.search(r"^\s*package\s+([\w.]+)", text, re.MULTILINE)
    package = pkg_match.group(1) if pkg_match else ""
    decls = TYPE_DECL.findall(text)
    stem = os.path.splitext(os.path.basename(path))[0]
    primary = next((n for _, n in decls if n == stem), decls[0][1] if decls else stem)
    kind = next((k for k, n in decls if n == primary), "file")
    imports = "\n".join(line for line in text.splitlines() if line.startswith("import "))
    tech = sorted(k for k, pat in TECH_IMPORT_PATTERNS.items() if re.search(pat, imports, re.MULTILINE))
    annotations = sorted(set(ANNOTATION.findall(text)) & ANNOTATIONS_OF_INTEREST)
    entry = Entry(
        file=os.path.relpath(path, root), package=package, primary_type=primary, kind=kind,
        types_in_file=len(decls), current_layer=current_layer_of(package), annotations=annotations, tech=tech,
        injects=constructor_injections(text, primary, path.endswith(".java")),
    )
    classify(entry, text)
    return entry


def render_md(entries: list[Entry], root: str) -> str:
    out = io.StringIO()
    out.write(f"# DSA inventory: {os.path.abspath(root)}\n\n")
    out.write("_Heuristic classification, confirm each row by reading the class._\n\n## Summary\n\n")
    out.write(f"- Files scanned: {len(entries)}\n")
    for title, counter in (
        ("Current layer", Counter(e.current_layer for e in entries)),
        ("Suggested layer", Counter(e.suggested_layer.split(' ')[0] for e in entries)),
        ("Role", Counter(e.role for e in entries)),
        ("Flags", Counter(f.split(':')[0] for e in entries for f in e.flags)),
    ):
        out.write(f"- {title}: " + ", ".join(f"{k}={v}" for k, v in counter.most_common()) + "\n")
    eps = [e for e in entries if e.role == "entry-point"]
    out.write(f"\n## Entry points ({len(eps)}) → one use case each (start the use-case list here)\n\n")
    out.write("| Entry point | Annotations | Injects | Flags |\n|---|---|---|---|\n")
    for e in eps:
        out.write(f"| `{e.primary_type}` ({e.file}) | {' '.join(e.annotations)} | {', '.join(e.injects)} | {'; '.join(e.flags)} |\n")
    flagged = [e for e in entries if e.flags and e.role != "entry-point"]
    out.write(f"\n## Flagged classes ({len(flagged)})\n\n| Class | Current | Suggested | Role | Flags |\n|---|---|---|---|---|\n")
    for e in flagged:
        out.write(f"| `{e.primary_type}` ({e.file}) | {e.current_layer} | {e.suggested_layer} | {e.role} | {'; '.join(e.flags)} |\n")
    out.write("\n## All classes\n\n| File | Type | Current | Suggested | Role | Tech | Injects | Flags |\n|---|---|---|---|---|---|---|---|\n")
    for e in entries:
        out.write(f"| {e.file} | {e.kind} `{e.primary_type}` | {e.current_layer} | {e.suggested_layer} | {e.role} | "
                  f"{','.join(e.tech)} | {', '.join(e.injects)} | {'; '.join(e.flags)} |\n")
    return out.getvalue()


def render_csv(entries: list[Entry]) -> str:
    out = io.StringIO()
    writer = csv.writer(out)
    writer.writerow(["file", "package", "type", "kind", "current_layer", "suggested_layer", "role", "annotations", "tech", "injects", "flags"])
    for e in entries:
        writer.writerow([e.file, e.package, e.primary_type, e.kind, e.current_layer, e.suggested_layer, e.role,
                         " ".join(e.annotations), " ".join(e.tech), " ".join(e.injects), " | ".join(e.flags)])
    return out.getvalue()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--format", choices=["md", "csv", "json"], default="md")
    parser.add_argument("--out")
    parser.add_argument("--include-generated", action="store_true")
    args = parser.parse_args()

    sources = find_sources(args.root, args.include_generated)
    if not sources:
        print(f"No Kotlin/Java files under */src/main/* in {args.root}", file=sys.stderr)
        return 1
    entries = [e for e in (analyse(p, args.root) for p in sources) if e]
    rendered = {"md": lambda: render_md(entries, args.root), "csv": lambda: render_csv(entries),
                "json": lambda: json.dumps([asdict(e) for e in entries], indent=2)}[args.format]()
    if args.out:
        os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(rendered)
        print(f"Wrote {len(entries)} entries to {args.out}", file=sys.stderr)
    else:
        sys.stdout.write(rendered)
    return 0


if __name__ == "__main__":
    sys.exit(main())
