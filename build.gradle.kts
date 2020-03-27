
group = "no.nav.syfo"
version = "1.0.0-SNAPSHOT"

val coroutinesVersion = "1.3.4"
val ktorVersion = "1.3.2"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "5.1"
val prometheusVersion = "0.8.0"
val kluentVersion = "1.49"
val junitJupiterVersion = "5.6.0"
val jacksonVersion = "2.9.7"
val smCommonVersion = "1.84cb553"
val kafkaEmbeddedVersion = "2.3.0"
val postgresVersion = "42.2.5"
val flywayVersion = "5.2.4"
val hikariVersion = "3.3.0"
val vaultJavaDriveVersion = "3.1.0"
val nimbusdsVersion = "7.5.1"
val postgresEmbeddedVersion = "0.13.3"
val mockkVersion = "1.9.3"

plugins {
    kotlin("jvm") version "1.3.71"
    id("org.jmailen.kotlinter") version "2.1.1"
    id("com.diffplug.gradle.spotless") version "3.24.0"
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/kotlin/ktor")
    maven(url = "https://dl.bintray.com/spekframework/spek-dev")
    maven(url = "https://kotlin.bintray.com/kotlinx")
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-networking:$smCommonVersion")
    implementation ("no.nav.helse:syfosm-common-rest-sts:$smCommonVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.bettercloud:vault-java-driver:$vaultJavaDriveVersion")

    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
    testImplementation("com.opentable.components:otj-pg-embedded:$postgresEmbeddedVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }
    create("printVersion") {
        doLast {
            println(project.version)
        }
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "12"
    }

    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        transform(com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnit()
        testLogging {
            showStandardStreams = true
        }
    }

    "check" {
        dependsOn("formatKotlin")
    }
}