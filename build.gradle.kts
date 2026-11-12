plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.kiokouwhite"
version = "0.1.0-SNAPSHOT"
description = "Crossfight UHC - un UHC à personnages pour Paper 1.21"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("-Xlint:deprecation")
    }
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("CrossfightUHC")
        // Le plugin n'utilise que des noms officiels (Mojang) : Paper n'a pas a le retraduire au demarrage.
        manifest { attributes["paperweight-mappings-namespace"] = "mojang" }
    }
    build { dependsOn(shadowJar) }
    test { useJUnitPlatform() }
}
