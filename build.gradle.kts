import java.net.URI

// configuration variables

val javaTargetVersion = "1.8"
val defaultVersion = "0.1.0-SNAPSHOT"
val flowVersion = "0.6.0.20210912061003-SNAPSHOT"

// other variables

fun getProp(name: String, defaultValue: String? = null): String? {
    return project.findProperty("flow.$name")?.toString()?.trim()?.ifBlank { null }
        ?: project.findProperty(name)?.toString()?.trim()?.ifBlank { null }
        ?: defaultValue
}

group = "com.nftco.contracts"
version = when {
    getProp("version") !in setOf("unspecified", null) -> { getProp("version")!! }
    getProp("snapshotDate") != null -> { "${defaultVersion.replace("-SNAPSHOT", "")}.${getProp("snapshotDate")!!}-SNAPSHOT" }
    else -> { defaultVersion }
}

plugins {
    id("org.jetbrains.dokka") version "1.4.20"
    kotlin("jvm") version "1.5.10"
    idea
    jacoco
    signing
    `java-library`
    `java-test-fixtures`
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin") version "1.0.0"
    id("org.jmailen.kotlinter") version "3.4.0"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    /*
        the following repository is required because the trusted data framework
        is not available on maven central.
    */
    maven { url = URI("https://jitpack.io") }
    maven { url = URI("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-reflect:1.5.10")
    dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:1.4.20")

    api("com.nftco:flow-jvm-sdk:$flowVersion")

    testApi("org.junit.jupiter:junit-jupiter:5.7.1")
    testApi("org.assertj:assertj-core:3.19.0")
    testFixturesImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testFixturesApi(testFixtures("com.nftco:flow-jvm-sdk:$flowVersion"))
}

tasks {

    test {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showStackTraces = true
            showCauses = true
        }
        finalizedBy("jacocoTestReport")
    }

    compileKotlin {
        sourceCompatibility = javaTargetVersion
        targetCompatibility = javaTargetVersion

        kotlinOptions {
            jvmTarget = javaTargetVersion
            apiVersion = "1.5"
            languageVersion = "1.5"
            freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
        }
    }

    compileTestKotlin {
        sourceCompatibility = javaTargetVersion
        targetCompatibility = javaTargetVersion

        kotlinOptions {
            jvmTarget = javaTargetVersion
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    jacocoTestReport {
        dependsOn(test)
        reports {
            html.isEnabled = true
            xml.isEnabled = true
            csv.isEnabled = false
        }
    }

    jacoco {
        toolVersion = "0.8.7"
    }

    kotlinter {
        ignoreFailures = false
        indentSize = 4
        reporters = arrayOf("checkstyle", "plain", "html")
        experimentalRules = false

        // be sure to update .editorconfig in the root as well
        disabledRules = arrayOf(
            "filename",
            "no-wildcard-imports",
            "import-ordering",
            "chain-wrapping"
        )
    }

    val documentationJar by creating(Jar::class) {
        dependsOn(dokkaHtml)
        archiveClassifier.set("javadoc")
        from(dokkaHtml.get().outputs)
    }

    val sourcesJar by creating(Jar::class) {
        dependsOn(classes)
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource + sourceSets["testFixtures"].allSource)
    }

    artifacts {
        add("archives", documentationJar)
        add("archives", sourcesJar)
    }

    nexusPublishing {
        repositories {
            sonatype {
                if (getProp("sonatype.nexusUrl") != null) {
                    nexusUrl.set(uri(getProp("sonatype.nexusUrl")!!))
                }
                if (getProp("sonatype.snapshotRepositoryUrl") != null) {
                    snapshotRepositoryUrl.set(uri(getProp("sonatype.snapshotRepositoryUrl")!!))
                }
                if (getProp("sonatype.username") != null) {
                    username.set(getProp("sonatype.username")!!)
                }
                if (getProp("sonatype.password") != null) {
                    password.set(getProp("sonatype.password")!!)
                }
            }
        }
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(project.components["java"])
                artifact(documentationJar)
                artifact(sourcesJar)

                pom {
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    name.set(project.name)
                    url.set("https://github.com/the-nft-company/nftco-smart-contract")
                    description.set("NFTco TenantService contracts")
                    scm {
                        url.set("https://github.com/the-nft-company/nftco-smart-contracts")
                        connection.set("scm:git:git@github.com/the-nft-company/nftco-smart-contracts.git")
                        developerConnection.set("scm:git:git@github.com/the-nft-company/nftco-smart-contracts.git")
                    }
                    developers {
                        developer {
                            name.set("NFTco")
                            url.set("https://nftco.com")
                        }
                    }
                }
            }
        }
    }

    signing {
        if (getProp("signing.key") != null) {
            useInMemoryPgpKeys(getProp("signing.key"), getProp("signing.password"))
        } else {
            useGpgCmd()
        }
        sign(publishing.publications)
    }
}
