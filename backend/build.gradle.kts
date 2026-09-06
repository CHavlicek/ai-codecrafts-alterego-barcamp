// Backend build: Spring Boot 3.x on Java 21 + Gradle Kotlin DSL.
// Constitution v1.1.0 Technology Standards (Backend table) + Principle III coverage gate
// + Principle IX test pyramid (per-tier Gradle tasks).

plugins {
    java
    checkstyle
    jacoco
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.sonarqube") version "5.1.0.4882"
}

group = "com.aiavatar"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web + validation + JSON (Jackson via starter-web)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Actuator for health/metrics (research.md R1)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Resilient HTTP — Principle IV
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")

    // Structured JSON logging — research.md R1
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // Photo size reduction before outbound Gemini call — 003 research.md R4.
    // Single-jar, MIT-licensed, no transitive deps; handles EXIF orientation.
    implementation("net.coobird:thumbnailator:0.4.20")

    // SMTP send seam for 023 — wires JavaMailSender via spring-boot-starter-mail.
    // Auto-configuration activates only when spring.mail.host is non-blank, so
    // the feature ships "wired but unprovisioned" by default (FR-2311 / FR-2318).
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Metrics (Micrometer ships with Actuator; explicit pin not required)

    // Test stack — Principle III
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    // OpenAPI contract validation against contracts/alter-egos.openapi.yaml
    testImplementation("com.atlassian.oai:swagger-request-validator-mockmvc:2.43.0")
    // WireMock for fault-injection integration tests against the Gemini
    // endpoint — 003 research.md R7.
    testImplementation("org.wiremock:wiremock-standalone:3.3.1")
    // ArchUnit — enforces the layer convention (Principle VII) and the
    // provider-seam invariant (Principle VIII) in the `arch/` test tier.
    // Test-only; Apache-2.0; no known CVEs at time of 024 plan.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Forward the on-demand visual-snapshot toggle used by
    // PosterTextOverlayDemo (`-Dposter.demo=true`) to the forked test JVM.
    System.getProperty("poster.demo")?.let { systemProperty("poster.demo", it) }
}

// ── Per-tier test tasks (Constitution Principle IX) ───────────────────────────
// Each tier filters by package under com.aiavatar.alterego.<tier>.**. The
// default `test` task aggregates the five tiers; it does not itself execute
// tests (its filter excludes everything) so each test runs exactly once and
// jacoco aggregates `executionData` across all five tier outputs.
//
//   ./gradlew :backend:unitTest         pure tests, no Spring   (< 10s SC-003)
//   ./gradlew :backend:serviceTest      single-slice tests
//   ./gradlew :backend:contractTest     OpenAPI shape pin
//   ./gradlew :backend:integrationTest  @SpringBootTest end-to-end
//   ./gradlew :backend:archTest         ArchUnit layer / seam rules

fun org.gradle.api.tasks.TaskContainer.registerTierTest(
    name: String, packagePattern: String, desc: String
) = register(name, Test::class) {
    description = desc
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching(packagePattern)
        isFailOnNoMatchingTests = false   // tiers may be empty in early commits
    }
}

val unitTest        = tasks.registerTierTest("unitTest",
    "com.aiavatar.alterego.unit.**",
    "Unit tier — pure tests, no Spring context. Target: < 10s wall-clock (Principle IX, SC-003).")
val serviceTest     = tasks.registerTierTest("serviceTest",
    "com.aiavatar.alterego.service.**",
    "Service tier — single-slice tests with declared collaborators (Principle IX).")
val contractTest    = tasks.registerTierTest("contractTest",
    "com.aiavatar.alterego.contract.**",
    "Contract tier — HTTP shape pinned against OpenAPI YAMLs (Principle IX).")
val integrationTest = tasks.registerTierTest("integrationTest",
    "com.aiavatar.alterego.integration.**",
    "Integration tier — @SpringBootTest end-to-end (Principle IX).")
val archTest        = tasks.registerTierTest("archTest",
    "com.aiavatar.alterego.arch.**",
    "Architecture tier — ArchUnit rules (Principles VII & VIII).")

val tierTests = listOf(unitTest, serviceTest, contractTest, integrationTest, archTest)

tasks.test {
    description = "Aggregates all five tiers (Principle IX). Does not itself execute tests."
    filter {
        excludeTestsMatching("*")
        isFailOnNoMatchingTests = false
    }
    dependsOn(tierTests)
    finalizedBy(tasks.jacocoTestReport)
}

// ── Coverage gate (Principle III: ≥ 90% line coverage) ────────────────────────
jacoco {
    toolVersion = "0.8.12"
}

// Spring Boot's @SpringBootApplication bootstrap is the one class where a
// unit test adds no value (everything meaningful is already covered by the
// RANDOM_PORT integration tests booting the real context). Exclude from the
// coverage metric so Principle III's ≥ 90% gate isn't dragged by its main()
// three-liner.
val jacocoClassDirExcludes = listOf(
    "com/aiavatar/alterego/AlterEgoApplication.class",
)

tasks.jacocoTestReport {
    dependsOn(tierTests)
    executionData.setFrom(
        tierTests.map { fileTree("$buildDir/jacoco").include("${it.name}.exec") }
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map { dir ->
            fileTree(dir) { exclude(jacocoClassDirExcludes) }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tierTests)
    executionData.setFrom(
        tierTests.map { fileTree("$buildDir/jacoco").include("${it.name}.exec") }
    )
    classDirectories.setFrom(
        files(classDirectories.files.map { dir ->
            fileTree(dir) { exclude(jacocoClassDirExcludes) }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// ── Checkstyle ────────────────────────────────────────────────────────────────
checkstyle {
    toolVersion = "10.18.2"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false
    maxWarnings = 0
}

// ── SonarQube — invoked via `./gradlew sonar` per constitutional Workflow §6 ──
sonar {
    properties {
        property("sonar.projectKey", "ai-avatar-backend")
        property("sonar.projectName", "AI-Avatar Backend")
        property("sonar.host.url", System.getenv("SONAR_HOST_URL") ?: "http://localhost:9000")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
    }
}
