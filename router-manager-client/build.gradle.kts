plugins {
	`java-library`
	id("com.google.protobuf") version "0.9.5"
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

val routerManagementApiVersion: String =
	project.findProperty("routerManagementApiVersion") as String?
		?: System.getenv("ROUTER_MANAGEMENT_API_VERSION")
		?: "1.0.0"

val routerManagementApiProto by configurations.creating {
	isCanBeConsumed = false
	isCanBeResolved = true
}

dependencies {
	routerManagementApiProto("ru.dkalchenko:router-management-api:$routerManagementApiVersion")
	api("io.grpc:grpc-stub:1.76.0")
	api("io.grpc:grpc-protobuf:1.76.0")
	api("com.google.protobuf:protobuf-java:4.32.0")
	compileOnly("javax.annotation:javax.annotation-api:1.3.2")
}

val extractedProtoDir = layout.buildDirectory.dir("extracted-proto/router-management-api")

val unpackRouterManagementApiProto by tasks.registering(Sync::class) {
	from({
		routerManagementApiProto.resolve().map { jarFile ->
			zipTree(jarFile).matching {
				include("proto/**")
			}
		}
	})
	into(extractedProtoDir)
}

sourceSets {
	main {
		proto {
			srcDir(extractedProtoDir)
		}
	}
}

protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc:4.32.0"
	}
	plugins {
		create("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java:1.76.0"
		}
	}
	generateProtoTasks {
		all().forEach { task ->
			task.dependsOn(unpackRouterManagementApiProto)
			task.plugins {
				create("grpc")
			}
		}
	}
}

tasks.named<ProcessResources>("processResources") {
	dependsOn(unpackRouterManagementApiProto)
	inputs.dir(extractedProtoDir)
}

val nexusUrl: String = project.findProperty("nexus.url") as String? ?: System.getenv("NEXUS_URL") ?: "http://localhost:8082/repository/maven-releases/"
val nexusUser: String = project.findProperty("nexus.user") as String? ?: System.getenv("NEXUS_USER") ?: "admin"
val nexusPassword: String = project.findProperty("nexus.password") as String? ?: System.getenv("NEXUS_PASSWORD") ?: "admin"

publishing {
	publications {
		create<MavenPublication>("routerManagerClient") {
			from(components["java"])
			artifactId = "router-manager-client"
			pom {
				name.set("Router Manager Client")
				description.set("Generated Java gRPC client for Router Management Service API")
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
