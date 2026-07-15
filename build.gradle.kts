plugins {
    java
}

group = "ru.sorfin"
version = "1.0.1"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.6-R0.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("WorldHeightLimit-v${project.version}-Paper-Purpur-Folia-1.21.x.jar")
}

tasks.register<Copy>("copySpigotBukkitJar") {
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("libs"))
    rename { "WorldHeightLimit-v${project.version}-Spigot-Bukkit-1.21.x.jar" }
}

tasks.build {
    dependsOn("copySpigotBukkitJar")
}
