val versions = mapOf(
	"confluentVersion" to "8.1.1",
	"apacheAvroVersion" to "1.12.1",
	"clickhouseVersion" to "0.9.6",
	"mapstructVersion" to "1.6.3",
	"liquibaseClickhouseVersion" to "0.9.0",
	"awaitilityVersion" to "4.3.0",
	"lombokMapstructBindingVersion" to "0.2.0",
	"lz4Version" to "1.10.2"
)

plugins {
	java
	id("org.springframework.boot") version "3.5.10"
	id("io.spring.dependency-management") version "1.1.7"
	jacoco
}

group = "ru.dkalchenko"
version = "1.0.0"
description = "Сервис для обработки событий IoT-устройств"

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

dependencies {
	// AVRO SCHEMAS (JAR из Nexus)
	implementation("ru.dkalchenko:iot-avro-schemas:1.0.0")

	// SPRING
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.kafka:spring-kafka")
	
	// AVRO & SCHEMA REGISTRY
	implementation("io.confluent:kafka-avro-serializer:${versions["confluentVersion"]}")
	implementation("io.confluent:kafka-schema-registry-client:${versions["confluentVersion"]}")
	implementation("org.apache.avro:avro:${versions["apacheAvroVersion"]}")

	// OBSERVABILITY
	runtimeOnly("io.micrometer:micrometer-registry-otlp")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	implementation("io.micrometer:micrometer-tracing-bridge-otel")
	implementation("io.opentelemetry:opentelemetry-exporter-otlp")
	implementation("io.opentelemetry:opentelemetry-sdk")
	implementation("io.micrometer:micrometer-observation")

	// PERSISTENCE
	implementation("org.liquibase:liquibase-core")
	implementation("io.goodforgod:liquibase-clickhouse:${versions["liquibaseClickhouseVersion"]}")
	implementation("com.clickhouse:clickhouse-jdbc:${versions["clickhouseVersion"]}")
	implementation("com.clickhouse:clickhouse-http-client:${versions["clickhouseVersion"]}")

	// HELPERS
	implementation("org.mapstruct:mapstruct:${versions["mapstructVersion"]}")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.mapstruct:mapstruct-processor:${versions["mapstructVersion"]}")
	annotationProcessor("org.projectlombok:lombok-mapstruct-binding:${versions["lombokMapstructBindingVersion"]}")
	runtimeOnly("org.projectlombok:lombok")

	// TEST
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.testcontainers:testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:kafka")
	testImplementation("org.awaitility:awaitility:${versions["awaitilityVersion"]}")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
}

// Конфликт capability: org.lz4:lz4-java (liquibase, kafka) vs at.yawk.lz4:lz4-java (clickhouse-jdbc)
configurations.all {
	resolutionStrategy.capabilitiesResolution.withCapability("org.lz4:lz4-java") {
		select("at.yawk.lz4:lz4-java:${versions["lz4Version"]}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.jacocoTestReport {
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

tasks.bootRun {
	environment("SPRING_PROFILES_ACTIVE", "local")
	jvmArgs = listOf(
		"--enable-native-access=ALL-UNNAMED",
		"--sun-misc-unsafe-memory-access=allow"
	)
}
