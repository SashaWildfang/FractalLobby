plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.sasha"
version = "1.62.0"
description = "Lobby core for Fractal Network: scoreboard with network player counts, lobby protection, void rescue and join items. Built by Sasha Wildfang."

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    // Compiled against the oldest supported API so the jar runs on Paper 1.21.11 and every newer release.
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Optional: only used when PlaceholderAPI is installed.
    compileOnly("me.clip:placeholderapi:2.11.6")
    // Optional: rank prefixes for chat, nametags and the tab list.
    compileOnly("net.luckperms:api:5.4")
    // Bundled: the network database's connection pool (Paper already has the MySQL driver and slf4j).
    implementation("com.zaxxer:HikariCP:6.3.0") {
        exclude(group = "org.slf4j")
    }

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    // Paper 1.21.11 requires Java 21; newer servers run on newer JVMs, which load Java 21 bytecode fine.
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked", "-Xlint:removal"))
    }

    processResources {
        val props = mapOf("version" to project.version, "description" to project.description)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveClassifier = "plain"
    }

    shadowJar {
        archiveClassifier = ""
        relocate("com.zaxxer.hikari", "com.sasha.lobby.lib.hikari")
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("1.21.11")
    }
}
