plugins {
    java
}

group = "dev.cody"
version = "0.1.0"
description = "A lightweight companion pet for Paper servers"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://api.modrinth.com/maven")
}

val paperApiVersion = "26.2.build.134-stable"
val rpEngineVersion = "0.2.26"

dependencies {
    // Paper 26.2 API (Java 25 bytecode). Provided by the server at runtime.
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    // RP Engine provides the AddonContent API at runtime. compileOnly so its
    // classes are not bundled into ChimeraCompanion.
    compileOnly("maven.modrinth:rp-engine:$rpEngineVersion")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching(listOf("plugin.yml", "content/chimera/pack.yml")) {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName = "ChimeraCompanion"
    // RP Engine reads the content folder carried by the plugin jar. The client
    // assets keep their chimera namespace when copied into the pack.
    from("resourcepack/assets") {
        into("content/chimera/resourcepack/assets")
    }
}

// 打包资源包：./gradlew resourcePack -> build/resourcepack/ChimeraCompanion-ResourcePack.zip
tasks.register<Zip>("resourcePack") {
    description = "Builds the client resource pack zip"
    from("resourcepack") {
        exclude("README.md")
    }
    archiveFileName = "ChimeraCompanion-ResourcePack.zip"
    destinationDirectory = layout.buildDirectory.dir("resourcepack")
}
