plugins {
	`java-library`
	`maven-publish`
}

group = "ru.dkalchenko"
version = "1.0.0"

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(24))
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

tasks.jar {
	archiveBaseName.set("router-management-api")
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	from("api") {
		into("proto/api")
	}
}

val nexusUrl: String = project.findProperty("nexus.url") as String? ?: System.getenv("NEXUS_URL") ?: "http://localhost:8082/repository/maven-releases/"
val nexusUser: String = project.findProperty("nexus.user") as String? ?: System.getenv("NEXUS_USER") ?: "admin"
val nexusPassword: String = project.findProperty("nexus.password") as String? ?: System.getenv("NEXUS_PASSWORD") ?: "admin"

publishing {
	publications {
		create<MavenPublication>("routerManagementApi") {
			from(components["java"])
			artifactId = "router-management-api"
			pom {
				name.set("Router Management API Contracts")
				description.set("gRPC/protobuf contracts for Router Management Service (proto only)")
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
