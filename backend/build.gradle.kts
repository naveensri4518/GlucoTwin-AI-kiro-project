import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
}

group = "com.glucotwin"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.19.8")
    }
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Spring AI — OpenAI adapter (Phase 13: controlled LLM explanation layer)
    // Artifact was renamed from spring-ai-openai-spring-boot-starter at GA.
    // spring-ai-starter-model-openai:1.0.1 is the current GA for Spring Boot 3.3.
    implementation(platform("org.springframework.ai:spring-ai-bom:1.0.1"))
    implementation("org.springframework.ai:spring-ai-starter-model-openai:1.0.1")

    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // Database
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")

    // Observability
    implementation("io.micrometer:micrometer-registry-otlp")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("net.jqwik:jqwik:1.8.3")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")

    // Redis testcontainers (uses the generic module)
    testImplementation("org.testcontainers:testcontainers")

    testAnnotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
}

// Default test task excludes integration tests (require Docker)
tasks.withType<Test> {
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
        excludeTags("integration")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

// Separate task to run integration tests (requires Docker)
tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeEngines("junit-jupiter")
        includeTags("integration")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    description = "Runs integration tests (requires Docker)"
    group = "verification"
}

// Phase 14: Evaluation, golden scenarios, provenance properties, and simulation isolation
// Fast, deterministic, no Docker required.
tasks.register<Test>("evaluationTest") {
    useJUnitPlatform {
        includeEngines("junit-jupiter", "jqwik")
        includeTags("evaluation")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    description = "Runs GlucoTwin evaluation and scenario tests"
    group = "verification"
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("glucotwin-backend.jar")
}
