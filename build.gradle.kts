import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

allprojects {
    group = "ru.dkalchenko"
}

/** HEAD по основному JAR в hosted Maven repo — если 200, считаем версию уже опубликованной. */
fun httpJarExistsInRepo(jarUrl: String, user: String, password: String): Boolean {
    val conn = URI(jarUrl).toURL().openConnection() as HttpURLConnection
    conn.requestMethod = "HEAD"
    conn.instanceFollowRedirects = true
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    val auth = Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
    conn.setRequestProperty("Authorization", "Basic $auth")
    return try {
        conn.responseCode == HttpURLConnection.HTTP_OK
    } finally {
        conn.disconnect()
    }
}

subprojects {
    afterEvaluate {
        if (!plugins.hasPlugin("maven-publish")) {
            return@afterEvaluate
        }
        val skipIfExists =
            findProperty("nexus.publish.skipIfExists")?.toString()?.toBooleanStrictOrNull() ?: true
        val force =
            findProperty("nexus.publish.force")?.toString()?.toBooleanStrictOrNull() ?: false
        val repoUrl =
            findProperty("nexus.url") as String?
                ?: System.getenv("NEXUS_URL")
                ?: "http://localhost:8082/repository/maven-releases/"
        val user =
            findProperty("nexus.user") as String? ?: System.getenv("NEXUS_USER") ?: "admin"
        val password =
            findProperty("nexus.password") as String? ?: System.getenv("NEXUS_PASSWORD") ?: "admin"

        val publishingExt = extensions.getByType(PublishingExtension::class.java)

        tasks.withType<PublishToMavenRepository>().configureEach {
            val publishTask = this
            onlyIf("skip publish when this version is already in Nexus") {
                if (!publishTask.name.endsWith("PublicationToMavenRepository")) {
                    return@onlyIf true
                }
                if (force || !skipIfExists) {
                    return@onlyIf true
                }
                val segment =
                    publishTask.name.removePrefix("publish").substringBefore("PublicationTo")
                val publicationName = segment.replaceFirstChar { it.lowercaseChar() }
                val pub =
                    publishingExt.publications.getByName(publicationName) as MavenPublication
                val proj = publishTask.project
                val groupId = pub.groupId ?: proj.group.toString()
                val artifactId = pub.artifactId
                val version = pub.version ?: proj.version.toString()
                val path = "${groupId.replace('.', '/')}/$artifactId/$version/$artifactId-$version.jar"
                val jarUrl = "${repoUrl.trimEnd('/')}/$path"
                val exists = try {
                    httpJarExistsInRepo(jarUrl, user, password)
                } catch (e: Exception) {
                    logger.warn(
                        "Could not check Nexus for $jarUrl ({}). Will attempt publish.",
                        e.message
                    )
                    false
                }
                if (exists) {
                    logger.lifecycle(
                        "Skipping publish: {}:{}:{} — JAR already in Nexus.",
                        groupId,
                        artifactId,
                        version
                    )
                }
                !exists
            }
        }
    }
}
