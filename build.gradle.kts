import org.gradle.configurationcache.extensions.capitalized

plugins {
    kotlin("jvm") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.5.0"
    id("xyz.jpenilla.run-paper") version "2.1.0"
    `maven-publish`
    `java-library`
    `kotlin-dsl`
    java
    idea
}

group = "love.chihuyu"
version = "0.0.1-SNAPSHOT"
val pluginVersion: String by project.ext

repositories {
    mavenCentral()
    maven("https://repo.codemc.org/repository/maven-public/")
//    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.hirosuke.me/snapshots/")
    maven("https://repo.purpurmc.org/snapshots")
}

/*
1.7.10~1.8.8: "org.github.paperspigot:paperspigot-api:$pluginVersion-R0.1-SNAPSHOT"
1.9.4~1.16.5: "com.destroystokyo.paper:paper-api:$pluginVersion-R0.1-SNAPSHOT"
1.17~1.19.4: "io.papermc.paper:paper-api:$pluginVersion-R0.1-SNAPSHOT"
 */

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:$pluginVersion-R0.1-SNAPSHOT")
//    compileOnly("io.papermc.paper:paper-api:$pluginVersion-R0.1-SNAPSHOT")
//    compileOnly("org.spigotmc:spigot-api:$pluginVersion-R0.1-SNAPSHOT")
//    compileOnly("org.bukkit:bukkit:$pluginVersion-R0.1-SNAPSHOT")
    compileOnly("love.chihuyu:chihuyu-utils:1.0.0-SNAPSHOT")
    compileOnly("dev.jorel:commandapi-core:9.0.3")
    compileOnly("dev.jorel:commandapi-kotlin:8.8.0")
    implementation(kotlin("stdlib"))
}

ktlint {
    ignoreFailures.set(true)
    disabledRules.add("no-wildcard-imports")
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        from(sourceSets.main.get().resources.srcDirs) {
            filter(org.apache.tools.ant.filters.ReplaceTokens::class, mapOf("tokens" to mapOf(
                "version" to project.version.toString(),
                "name" to project.name.capitalized(),
                "mainPackage" to "love.chihuyu.${project.name.lowercase().replace("-", "")}.${project.name.capitalized()}Plugin"
            )))
            filteringCharset = "UTF-8"
        }
    }

    shadowJar {
        exclude("org/slf4j/**")
        relocate("kotlin", "love.chihuyu.${project.name.capitalized().lowercase()}.lib.kotlin")
    }

    runServer {
        minecraftVersion(pluginVersion)
    }
}

publishing {
    repositories {
        maven {
            name = "repo"
            credentials(PasswordCredentials::class)
            url = uri(
                if (project.version.toString().endsWith("SNAPSHOT"))
                    "https://repo.hirosuke.me/snapshots/"
                else
                    "https://repo.hirosuke.me/releases/"
            )
        }
    }

    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

kotlin {
    jvmToolchain(17)
}

task("setup") {
    doFirst {
        val projectDir = project.projectDir
        projectDir.resolve("renovate.json").deleteOnExit()
        val srcDir = projectDir.resolve("src/main/kotlin/love/chihuyu/${project.name.capitalized().lowercase()}").apply(File::mkdirs)
        srcDir.resolve("${project.name.capitalized()}Plugin.kt").writeText(
            """
                package love.chihuyu.${project.name.capitalized().lowercase()}
                
                import org.bukkit.plugin.java.JavaPlugin
    
                class ${project.name.capitalized()}Plugin: JavaPlugin() {
                    companion object {
                        lateinit var ${project.name.capitalized()}Plugin: JavaPlugin
                    }
                
                    init {
                        ${project.name.capitalized()}Plugin = this
                    }
                }
            """.trimIndent()
        )
    }
}

task("generateActionsFile") {
    doFirst {
        val actionFile = projectDir.resolve(".github/workflows").apply(File::mkdirs)
        actionFile.resolve("deploy.yml").writeText(
            """
                name: Deploy
                on:
                  workflow_dispatch:
                  push:
                    branches:
                      - 'master'
                    paths-ignore:
                      - "**.md"
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    permissions:
                      contents: read
                    steps:
                      - uses: actions/checkout@v3
                      - name: Set up JDK 17
                        uses: actions/setup-java@v3
                        with:
                          java-version: '17'
                          distribution: 'temurin'
                      - name: Grant execute permission for gradlew
                        run: chmod +x gradlew
                      - name: Prepare gradle.properties
                        run: |
                          mkdir -p ${ "\$HOME" }/.gradle
                          echo ${ "repoUsername=\${{ secrets.DEPLOY_USERNAME }} "} >> ${ "\$HOME" }/.gradle/gradle.properties
                          echo ${ "repoPassword=\${{ secrets.DEPLOY_PASSWORD }}"} >> ${ "\$HOME" }/.gradle/gradle.properties
                      - name: Deploy
                        run: |
                          ./gradlew clean test publish
            """.trimIndent()
        )
    }
}