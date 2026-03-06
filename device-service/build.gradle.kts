val versions = mapOf(
	"confluentVersion" to "8.1.1",
	"apacheAvroVersion" to "1.12.1",
	"shardingSphereVersion" to "5.5.2",
	"postgresqlVersion" to "42.7.4",
	"iotAvroSchemasVersion" to "1.0.1",
	"springCloudDependenciesVersion" to "2025.0.0",
	"deviceServiceApiVersion" to "1.0.0"
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
description = "Сервис управления устройствами: REST API, READ из PostgreSQL, CREATE/UPDATE/DELETE через Kafka"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(24)
	}
}

repositories {
	mavenCentral()
	maven {
		url = uri("https://packages.confluent.io/maven/")
	}
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

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${versions["springCloudDependenciesVersion"]}")
	}
}

dependencies {
	// Device Service API (DTO + Feign-клиент) — (JAR из Nexus)
	implementation("ru.dkalchenko:device-service-api:${versions["deviceServiceApiVersion"]}")

	// AVRO SCHEMAS (JAR из Nexus)
	implementation("ru.dkalchenko:iot-avro-schemas:${versions["iotAvroSchemasVersion"]}")

	// SPRING
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.kafka:spring-kafka")

	// AVRO & SCHEMA REGISTRY
	implementation("io.confluent:kafka-avro-serializer:${versions["confluentVersion"]}")
	implementation("io.confluent:kafka-schema-registry-client:${versions["confluentVersion"]}")
	implementation("org.apache.avro:avro:${versions["apacheAvroVersion"]}")

	// DATABASE & SHARDING
	implementation("org.apache.shardingsphere:shardingsphere-jdbc:${versions["shardingSphereVersion"]}")
	runtimeOnly("org.postgresql:postgresql:${versions["postgresqlVersion"]}")

	// MIGRATIONS
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")

	// OBSERVABILITY
	runtimeOnly("io.micrometer:micrometer-registry-otlp")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	implementation("io.micrometer:micrometer-tracing-bridge-otel")
	implementation("io.opentelemetry:opentelemetry-exporter-otlp")
	implementation("io.micrometer:micrometer-observation")

	// HELPERS
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	runtimeOnly("org.projectlombok:lombok")

	// TEST
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.testcontainers:testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:kafka")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.awaitility:awaitility:4.3.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
}

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
		sourceSets.main.get().output.classesDirs.files.map { dir ->
			fileTree(dir) {
				exclude(
					"**/DeviceServiceApplication.class",
					"**/config/**",
					"**/api/**"
				)
			}
		}
	)
}

tasks.bootRun {
	environment("SPRING_PROFILES_ACTIVE", "local")
	val localEnv = file("src/main/resources/local.env")
	if (localEnv.exists()) {
		doFirst {
			localEnv.readLines()
				.filter { it.contains("=") && !it.trim().startsWith("#") }
				.forEach { line ->
					val eq = line.indexOf('=')
					if (eq > 0) environment(line.substring(0, eq).trim(), line.substring(eq + 1).trim())
				}
		}
	}
}

// Конфигурация для извлечения OpenAPI-спецификации из JAR зависимости device-service-api (без транзитивов)
val openApiSpecConfig by configurations.creating {
	isTransitive = false
}
dependencies {
	openApiSpecConfig("ru.dkalchenko:device-service-api:${versions["deviceServiceApiVersion"]}")
}

val openApiExtractDir = layout.buildDirectory.dir("openapi-extracted")

tasks.register<Copy>("extractOpenApiSpec") {
	description = "Извлекает OpenAPI-спецификацию из JAR device-service-api"
	from(zipTree(openApiSpecConfig.singleFile))
	into(openApiExtractDir)
	include("openapi/device-api.yaml")
}

val openApiOutputDir = file("build/generated/sources/openapi/server")
// Путь к спецификации после извлечения (extractOpenApiSpec должен выполниться первым)
val openApiSpecPath = file("build/openapi-extracted/openapi/device-api.yaml").absolutePath

tasks.named("openApiGenerate") {
	dependsOn(tasks["extractOpenApiSpec"])
}

openApiGenerate {
	generatorName.set("spring")
	inputSpec.set(openApiSpecPath)
	outputDir.set(openApiOutputDir.absolutePath)
	apiPackage.set("ru.dkalchenko.device.api.server")
	modelPackage.set("ru.dkalchenko.device.api.model")
	invokerPackage.set("ru.dkalchenko.device.api.server")
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
		"documentationProvider" to "none"
	))
	importMappings.set(mapOf(
		"Device" to "ru.dkalchenko.device.api.model.Device",
		"DeviceRequest" to "ru.dkalchenko.device.api.model.DeviceRequest",
		"CreateResponse" to "ru.dkalchenko.device.api.model.CreateResponse",
		"MessageResponse" to "ru.dkalchenko.device.api.model.MessageResponse"
	))
}

// Подключаем только серверный API (controller, delegate, Api); модели используем из зависимости device-service-api
sourceSets["main"].java.srcDir("${openApiOutputDir}/src/main/java")
sourceSets["main"].java.exclude(
	"**/ru/dkalchenko/device/api/model/**",
	"**/org/openapitools/configuration/**",
	"**/OpenApiGeneratorApplication.java"
)
tasks.compileJava {
	dependsOn(tasks.openApiGenerate)
}
