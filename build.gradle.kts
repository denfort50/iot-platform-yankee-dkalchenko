// Корневой проект — мультимодульная сборка микросервисов
// Каждый подпроект (events-collector-service, simple-event-sender) — самостоятельный модуль

allprojects {
    group = "ru.dkalchenko"
}

// При сборке из корня (в т.ч. Docker) подставляем локальный :iot-avro-schemas вместо артефакта из Nexus
subprojects {
    if (name != "iot-avro-schemas") {
        configurations.all {
            resolutionStrategy.dependencySubstitution {
                substitute(module("ru.dkalchenko:iot-avro-schemas"))
                    .using(project(":iot-avro-schemas"))
                    .because("use local project when building from root (e.g. Docker)")
            }
        }
    }
}
