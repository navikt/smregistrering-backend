import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.syfo"
version = "1.0.0"


val javaVersion = JvmTarget.JVM_21


val coroutinesVersion = "1.9.0"
val ktorVersion = "3.0.2"
val logbackVersion = "1.5.6"
val logstashEncoderVersion = "7.4"
val prometheusVersion = "0.16.0"
val junitJupiterVersion = "5.10.2"
val jacksonVersion = "2.18.1"
val postgresVersion = "42.7.3"
val flywayVersion = "10.11.0"
val hikariVersion = "5.1.0"
val nimbusdsVersion = "9.37.3"
val mockkVersion = "1.13.13"
val syfoXmlCodegenVerison = "2.0.1"
val jaxbApiVersion = "2.1"
val jaxbVersion = "2.3.0.1"
val javaxActivationVersion = "1.1.1"
val jaxwsApiVersion = "2.3.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val jaxwsToolsVersion = "2.3.1"
val javaTimeAdapterVersion = "1.1.3"
val commonsTextVersion = "1.12.0"
val kafkaVersion = "3.9.0"
val caffeineVersion = "3.1.8"
val postgresContainerVersion = "1.20.4"
val kotlinVersion = "2.1.0"
val commonsCodecVersion = "1.16.1"
val logbacksyslog4jVersion = "1.0.0"
val ktfmtVersion = "0.44"
val opentelemetryVersion = "2.3.0"
val nettycommonVersion = "4.1.115.Final"
val snappyJavaVersion = "1.1.10.6"


plugins {
    id("application")
    kotlin("jvm") version "2.1.0"
    id("com.diffplug.spotless") version "6.25.0"
    id("com.gradleup.shadow") version "8.3.5"
}

application {
    mainClass.set("no.nav.syfo.BootstrapKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}


kotlin {
    compilerOptions {
        jvmTarget = javaVersion
    }
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    constraints {
        implementation("io.netty:netty-common:$nettycommonVersion") {
            because("override transient version from io.ktor:ktor-server-netty")
        }
    }
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    constraints {
        implementation("commons-codec:commons-codec:$commonsCodecVersion"){
            because("override transient version from io.ktor:ktor-client-apache")
        }
    }

    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryVersion")


    implementation ("no.nav.helse.xml:xmlfellesformat:$syfoXmlCodegenVerison")
    implementation ("no.nav.helse.xml:sm2013:$syfoXmlCodegenVerison")
    implementation ("no.nav.helse.xml:kith-hodemelding:$syfoXmlCodegenVerison")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation ("org.apache.kafka:kafka_2.12:$kafkaVersion")
    constraints {
        implementation("org.xerial.snappy:snappy-java:$snappyJavaVersion") {
            because("override transient from org.apache.kafka:kafka_2.12")
        }
    }


    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    compileOnly("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation ("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation ("org.glassfish.jaxb:jaxb-runtime:$jaxbVersion")
    implementation ("javax.activation:activation:$javaxActivationVersion")

    implementation("com.github.ben-manes.caffeine:caffeine:$caffeineVersion")

    implementation ("javax.xml.ws:jaxws-api:$jaxwsApiVersion")
    implementation ("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation ("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation ("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation ("javax.activation:activation:$javaxActivationVersion")
    implementation("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }
    implementation("com.migesok:jaxb-java-time-adapters:$javaTimeAdapterVersion")

    implementation ("org.apache.commons:commons-text:$commonsTextVersion")

    implementation("com.papertrailapp:logback-syslog4j:$logbacksyslog4jVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.testcontainers:postgresql:$postgresContainerVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

tasks {
    shadowJar {
mergeServiceFiles {
     setPath("META-INF/services/org.flywaydb.core.extensibility.Plugin")
 }
        archiveBaseName.set("app")
        archiveClassifier.set("")
        isZip64 = true
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.syfo.BootstrapKt",
                ),
            )
        }
    }


    test {
        useJUnitPlatform {}
        testLogging {
            events("skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }


    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
        }
    }
}
