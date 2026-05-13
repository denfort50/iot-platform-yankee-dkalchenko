val versions = mapOf(
	"springCloudDependenciesVersion" to "2025.0.0",
	"resilience4jVersion" to "2.3.0",
	"grpcVersion" to "1.76.0",
	"wiremockVersion" to "3.13.1",
	"archunitVersion" to "1.4.1",
	"apiClientsVersion" to "1.0.0"
)

plugins {
	java
	id("org.springframework.boot") version "3.5.10"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.openapi.generator") version "7.6.0"
	jacoco
}

group = "ru.dkalchenko"
version = "1.0.0"
description = "Фасадный оркестратор: Feign/gRPC к device, event, router-management; сага обновления версии"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(24)
	}
}

repositories {
	mavenCentral()
	maven {
		name = "nexus"
		url = uri(project.findProperty("nexus.url") as String? ?: System.getenv("NEXUS_URL") ?: "http://localhost:8082/repository/maven-releases/")
		isAllowInsecureProtocol = true
		credentials {
			username = project.findProperty("nexus.user") as String? ?: System.getenv("NEXUS_USER") ?: "admin"
			password = project.findProperty("nexus.password") as String? ?: System.getenv("NEXUS_PASSWORD") ?: "admin"
		}
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${versions["springCloudDependenciesVersion"]}")
		mavenBom("io.grpc:grpc-bom:${versions["grpcVersion"]}")
		mavenBom("io.github.resilience4j:resilience4j-bom:${versions["resilience4jVersion"]}")
	}
}

dependencies {
	implementation(project(":api-orchestrator-api"))
	implementation(project(":device-service-api"))
	implementation(project(":event-service-api"))
	implementation(project(":router-manager-client"))

	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
	implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
	implementation("io.github.resilience4j:resilience4j-spring-boot3:${versions["resilience4jVersion"]}")
	implementation("io.github.resilience4j:resilience4j-reactor:${versions["resilience4jVersion"]}")
	implementation("io.github.resilience4j:resilience4j-micrometer:${versions["resilience4jVersion"]}")
	implementation("io.github.openfeign:feign-micrometer")
	runtimeOnly("io.micrometer:micrometer-registry-otlp")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	implementation("io.micrometer:micrometer-tracing-bridge-otel")
	implementation("io.opentelemetry:opentelemetry-exporter-otlp")
	// opentelemetry-grpc-1.6 публикуется только в alpha BOM (версия совпадает с opentelemetry-bom 1.49.x из Micrometer).
	implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:2.15.0-alpha"))
	implementation("io.opentelemetry.instrumentation:opentelemetry-grpc-1.6")
	implementation("io.micrometer:micrometer-observation")

	implementation("io.grpc:grpc-netty-shaded:${versions["grpcVersion"]}")
	implementation("io.grpc:grpc-core:${versions["grpcVersion"]}")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.springframework.cloud:spring-cloud-contract-wiremock")
	testImplementation("org.wiremock:wiremock-standalone:${versions["wiremockVersion"]}")
	testImplementation("org.testcontainers:testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("io.grpc:grpc-testing:${versions["grpcVersion"]}")
	testImplementation("com.tngtech.archunit:archunit-junit5:${versions["archunitVersion"]}")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val openApiOutputDir = file("build/generated/sources/openapi/server")

openApiGenerate {
	generatorName.set("spring")
	inputSpec.set(project(":api-orchestrator-api").file("src/main/resources/openapi/orchestrator-api.yaml").absolutePath)
	outputDir.set(openApiOutputDir.absolutePath)
	apiPackage.set("ru.dkalchenko.orchestrator.adapter.inbound.api.generated")
	modelPackage.set("ru.dkalchenko.orchestrator.api.model")
	invokerPackage.set("ru.dkalchenko.orchestrator.adapter.inbound.api.generated")
	configOptions.set(mapOf(
		"library" to "spring-boot",
		"delegatePattern" to "true",
		"useJakartaEe" to "true",
		"useBeanValidation" to "true",
		"useSpringBoot3" to "true",
		"useResponseEntity" to "true",
		"openApiNullable" to "false",
		"dateLibrary" to "java8",
		"serializationLibrary" to "jackson",
		"performBeanValidation" to "true",
		"documentationProvider" to "none",
		"useTags" to "true",
		"reactive" to "true"
	))
	importMappings.set(mapOf(
		"SendCommandRequestBody" to "ru.dkalchenko.orchestrator.api.model.SendCommandRequestBody",
		"SendCommandResponseBody" to "ru.dkalchenko.orchestrator.api.model.SendCommandResponseBody",
		"CommandsPollRequestBody" to "ru.dkalchenko.orchestrator.api.model.CommandsPollRequestBody",
		"CommandMessage" to "ru.dkalchenko.orchestrator.api.model.CommandMessage",
		"CommandsPollResponseBody" to "ru.dkalchenko.orchestrator.api.model.CommandsPollResponseBody",
		"CommandsAckRequestBody" to "ru.dkalchenko.orchestrator.api.model.CommandsAckRequestBody",
		"CommandsAckResponseBody" to "ru.dkalchenko.orchestrator.api.model.CommandsAckResponseBody",
		"RegisterRouterRequestBody" to "ru.dkalchenko.orchestrator.api.model.RegisterRouterRequestBody",
		"RegisterRouterResponseBody" to "ru.dkalchenko.orchestrator.api.model.RegisterRouterResponseBody",
		"DeviceRequest" to "ru.dkalchenko.orchestrator.api.model.DeviceRequest",
		"Device" to "ru.dkalchenko.orchestrator.api.model.Device",
		"CreateResponse" to "ru.dkalchenko.orchestrator.api.model.CreateResponse",
		"MessageResponse" to "ru.dkalchenko.orchestrator.api.model.MessageResponse",
		"DeviceVersionUpdateRequest" to "ru.dkalchenko.orchestrator.api.model.DeviceVersionUpdateRequest",
		"VersionUpdateResult" to "ru.dkalchenko.orchestrator.api.model.VersionUpdateResult",
		"Event" to "ru.dkalchenko.orchestrator.api.model.Event",
		"EventPage" to "ru.dkalchenko.orchestrator.api.model.EventPage",
		"ProblemDetails" to "ru.dkalchenko.orchestrator.api.model.ProblemDetails"
	))
}

sourceSets["main"].java.srcDir("${openApiOutputDir}/src/main/java")
sourceSets["main"].java.exclude(
	"**/ru/dkalchenko/orchestrator/api/model/**",
	"**/org/openapitools/configuration/**",
	"**/OpenApiGeneratorApplication.java"
)

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
	classDirectories.setFrom(
		files(sourceSets["main"].output.classesDirs.files.map { dir ->
			fileTree(dir) {
				exclude(
					"**/ru/dkalchenko/orchestrator/adapter/inbound/api/generated/**",
					"**/ApiOrchestratorApplication.class"
				)
			}
		})
	)
}

tasks.compileJava {
	dependsOn(tasks.openApiGenerate)
}
