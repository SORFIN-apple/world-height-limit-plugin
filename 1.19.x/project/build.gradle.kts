plugins {
    java
    id("com.gradleup.shadow") version "8.3.10"
}

group = "ru.sorfin"
version = "1.2.1"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

sourceSets.main {
    java.srcDir("../../shared/server-metrics/src/main/java")
    resources.srcDir("../../shared/server-metrics/src/main/resources")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val bstatsPluginId = providers.gradleProperty("bstatsPluginId").orElse("34825")
    inputs.property("bstatsPluginId", bstatsPluginId)
    inputs.property("pluginVersion", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
    filesMatching("config.yml") {
        expand("bstatsPluginId" to bstatsPluginId.get())
    }
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveFileName.set("WorldHeightLimit-v${project.version}-Paper-Purpur-Folia-1.19.x.jar")
    relocate("org.bstats", "ru.sorfin.worldheightlimit.libs.bstats")
}

tasks.register<Copy>("copySpigotBukkitJar") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("libs"))
    rename { "WorldHeightLimit-v${project.version}-Spigot-Bukkit-1.19.x.jar" }
}

tasks.build {
    dependsOn("copySpigotBukkitJar")
}
