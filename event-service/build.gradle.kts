val versions = mapOf(
	"springCloudDependenciesVersion" to "2025.0.0",
	"clickhouseVersion" to "0.9.6",
	"lz4Version" to "1.10.2",
	"eventServiceApiVersion" to "1.0.0"
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
description = "Read-only REST API событий устройств из ClickHouse"

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

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${versions["springCloudDependenciesVersion"]}")
	}
}

dependencies {
	implementation(project(":event-service-api"))

	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")

	runtimeOnly("io.micrometer:micrometer-registry-otlp")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	implementation("io.micrometer:micrometer-tracing-bridge-otel")
	implementation("io.opentelemetry:opentelemetry-exporter-otlp")
	implementation("io.micrometer:micrometer-observation")

	implementation("com.clickhouse:clickhouse-jdbc:${versions["clickhouseVersion"]}")
	implementation("com.clickhouse:clickhouse-http-client:${versions["clickhouseVersion"]}")

	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	runtimeOnly("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.testcontainers:testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:clickhouse")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
}

configurations.all {
	resolutionStrategy.capabilitiesResolution.withCapability("org.lz4:lz4-java") {
		select("at.yawk.lz4:lz4-java:${versions["lz4Version"]}")
	}
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
					"**/EventServiceApplication.class",
					"**/config/**",
					"**/api/**"
				)
			}
		}
	)
}

tasks.bootRun {
	environment("SPRING_PROFILES_ACTIVE", "local")
	jvmArgs = listOf(
		"--enable-native-access=ALL-UNNAMED",
		"--sun-misc-unsafe-memory-access=allow"
	)
}

val openApiOutputDir = file("build/generated/sources/openapi/server")
val extractEventApiSpec by tasks.registering(Copy::class) {
	dependsOn(":event-service-api:processResources")
	from(project(":event-service-api").layout.projectDirectory.file("src/main/resources/openapi/event-api.yaml"))
	rename { "event-api.yaml" }
	into(layout.buildDirectory.dir("openapi-spec/event-service-api"))
}

openApiGenerate {
	generatorName.set("spring")
	inputSpec.set(layout.buildDirectory.file("openapi-spec/event-service-api/event-api.yaml").map { it.asFile.absolutePath })
	outputDir.set(openApiOutputDir.absolutePath)
	apiPackage.set("ru.dkalchenko.event.api.server")
	modelPackage.set("ru.dkalchenko.event.api.model")
	invokerPackage.set("ru.dkalchenko.event.api.server")
	configOptions.set(
		mapOf(
			"library" to "spring-boot",
			"delegatePattern" to "true",
			"useTags" to "true",
			"useJakartaEe" to "true",
			"useBeanValidation" to "true",
			"useSpringBoot3" to "true",
			"useResponseEntity" to "true",
			"openApiNullable" to "false",
			"dateLibrary" to "java8",
			"serializationLibrary" to "jackson",
			"performBeanValidation" to "true",
			"documentationProvider" to "none"
		)
	)
	importMappings.set(
		mapOf(
			"Event" to "ru.dkalchenko.event.api.model.Event",
			"EventPage" to "ru.dkalchenko.event.api.model.EventPage"
		)
	)
}
tasks.named("openApiGenerate") {
	dependsOn(extractEventApiSpec)
}

sourceSets["main"].java.srcDir("${openApiOutputDir}/src/main/java")
sourceSets["main"].java.exclude(
	"**/ru/dkalchenko/event/api/model/**",
	"**/org/openapitools/configuration/**",
	"**/OpenApiGeneratorApplication.java"
)
tasks.compileJava {
	dependsOn(tasks.openApiGenerate)
}
