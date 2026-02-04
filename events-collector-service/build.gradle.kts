val versions = mapOf(
	"confluentVersion" to "8.1.1",
	"apacheAvroVersion" to "1.12.1",
	"clickhouseVersion" to "0.9.6",
	"mapstructVersion" to "1.6.3",
	"liquibaseClickhouseVersion" to "0.9.0"
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
}

dependencies {
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
	implementation("io.opentelemetry:opentelemetry-exporter-otlp")
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
	annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
	runtimeOnly("org.projectlombok:lombok")

	// TEST
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.testcontainers:testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:kafka")
	testImplementation("org.awaitility:awaitility:4.2.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
}

// Конфликт capability: org.lz4:lz4-java (liquibase, kafka) vs at.yawk.lz4:lz4-java (clickhouse-jdbc)
configurations.all {
	resolutionStrategy.capabilitiesResolution.withCapability("org.lz4:lz4-java") {
		select("at.yawk.lz4:lz4-java:1.10.2")
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
