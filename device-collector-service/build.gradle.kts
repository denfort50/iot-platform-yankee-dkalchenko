val versions = mapOf(
	"confluentVersion" to "8.1.1",
	"apacheAvroVersion" to "1.12.1",
	"shardingSphereVersion" to "5.5.2",
	"postgresqlVersion" to "42.7.4"
)

plugins {
	java
	id("org.springframework.boot") version "3.5.10"
	id("io.spring.dependency-management") version "1.1.7"
	jacoco
}

group = "ru.dkalchenko"
version = "1.0.0"
description = "Сервис для обработки сообщений об устройствах из Kafka и сохранения в PostgreSQL"

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
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
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
}

tasks.jacocoTestReport {
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

tasks.bootRun {
	environment("SPRING_PROFILES_ACTIVE", "local")
}
