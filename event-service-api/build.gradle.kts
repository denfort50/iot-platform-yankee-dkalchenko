val versions = mapOf(
	"springCloudDependenciesVersion" to "2025.0.0",
	"jacksonDatatypeJsr310Version" to "2.21.0",
	"validationApiVersion" to "3.0.2"
)

plugins {
	java
	id("org.openapi.generator") version "7.6.0"
	id("io.spring.dependency-management") version "1.1.7"
	`maven-publish`
}

group = "ru.dkalchenko"
version = "1.0.0"

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

val openApiSpec = file("src/main/resources/openapi/event-api.yaml")
val openApiOutputDir = file("build/generated/sources/openapi")

openApiGenerate {
	generatorName.set("java")
	inputSpec.set(openApiSpec.absolutePath)
	outputDir.set(openApiOutputDir.absolutePath)
	packageName.set("ru.dkalchenko.event.api")
	apiPackage.set("ru.dkalchenko.event.api")
	modelPackage.set("ru.dkalchenko.event.api.model")
	invokerPackage.set("ru.dkalchenko.event.api")
	configOptions.set(mapOf(
		"library" to "feign",
		"useJakartaEe" to "true",
		"useBeanValidation" to "true",
		"openApiNullable" to "false",
		"dateLibrary" to "java8",
		"serializationLibrary" to "jackson"
	))
	typeMappings.set(mapOf(
		"date-time" to "java.time.OffsetDateTime"
	))
}

sourceSets["main"].java.srcDir("${openApiOutputDir}/src/main/java")
tasks.compileJava {
	dependsOn(tasks.openApiGenerate)
}

dependencies {
	implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
	implementation("io.github.openfeign:feign-okhttp")
	implementation("io.github.openfeign:feign-jackson")
	implementation("io.github.openfeign:feign-slf4j")
	implementation("io.github.openfeign:feign-form")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${versions["jacksonDatatypeJsr310Version"]}")
	implementation("jakarta.validation:jakarta.validation-api:${versions["validationApiVersion"]}")
}

tasks.named("build") {
	dependsOn(tasks["publish"])
}

val nexusUrl: String = project.findProperty("nexus.url") as String? ?: System.getenv("NEXUS_URL") ?: "http://localhost:8082/repository/maven-releases/"
val nexusUser: String = project.findProperty("nexus.user") as String? ?: System.getenv("NEXUS_USER") ?: "admin"
val nexusPassword: String = project.findProperty("nexus.password") as String? ?: System.getenv("NEXUS_PASSWORD") ?: "admin"

publishing {
	publications {
		create<MavenPublication>("eventServiceApi") {
			from(components["java"])
			artifactId = "event-service-api"
			pom {
				name.set("Event Service API")
				description.set("OpenAPI spec and Feign client for Event Service REST API")
			}
		}
	}
	repositories {
		maven {
			url = uri(nexusUrl)
			isAllowInsecureProtocol = true
			credentials {
				username = nexusUser
				password = nexusPassword
			}
		}
	}
}
