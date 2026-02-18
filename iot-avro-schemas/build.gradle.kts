plugins {
    java
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
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencies {
    implementation("io.confluent:kafka-schema-registry-client:8.1.1")
    implementation("org.apache.avro:avro:1.12.1")
}

// Регистрация Avro-схем в Schema Registry при сборке (Schema Registry — порт 8081, Nexus — 8082)
val schemaRegistryUrl: String = project.findProperty("schemaRegistry.url") as String? ?: System.getenv("SCHEMA_REGISTRY_URL") ?: "http://localhost:8081"

tasks.register<JavaExec>("registerSchemas") {
    group = "publishing"
    description = "Register Avro schemas from this module into Confluent Schema Registry"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ru.dkalchenko.avro.SchemaRegistryBootstrap")
    environment("SCHEMA_REGISTRY_URL", schemaRegistryUrl)
    doFirst {
        logger.lifecycle("Registering schemas to Schema Registry at $schemaRegistryUrl")
    }
}

// При сборке: регистрация схем в Schema Registry и публикация JAR в Nexus
tasks.named("build") {
    dependsOn(tasks["registerSchemas"], tasks["publish"])
}

// Публикация в Nexus (хост: localhost:8082, из docker-compose)
val nexusUrl: String = project.findProperty("nexus.url") as String? ?: System.getenv("NEXUS_URL") ?: "http://localhost:8082/repository/maven-releases/"
val nexusUser: String = project.findProperty("nexus.user") as String? ?: System.getenv("NEXUS_USER") ?: "admin"
val nexusPassword: String = project.findProperty("nexus.password") as String? ?: System.getenv("NEXUS_PASSWORD") ?: "admin"

publishing {
    publications {
        create<MavenPublication>("avroSchemas") {
            from(components["java"])
            artifactId = "iot-avro-schemas"
            pom {
                name.set("IoT Platform Avro Schemas")
                description.set("Shared Avro schemas for DeviceInfo, DeviceEvent, DeadLetterMessage")
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

// Перед публикацией зарегистрировать схемы в Schema Registry
tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(tasks["registerSchemas"])
}
