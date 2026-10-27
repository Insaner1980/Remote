buildscript {
    dependencies {
        constraints {
            // Patch Dependency-Check's parser dependencies before resolving their metadata.
            classpath("com.fasterxml.jackson.core:jackson-core:2.22.4")
            classpath("com.fasterxml.jackson.core:jackson-databind:2.22.4")
            classpath("org.jsoup:jsoup:1.23.2")
            // Keep AGP's transitive build tools on patched releases.
            classpath("org.apache.commons:commons-lang3:3.21.0")
            classpath("org.bitbucket.b_c:jose4j:0.9.7")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
            classpath("org.bouncycastle:bcprov-jdk18on:1.86")
            classpath("org.jdom:jdom2:2.0.6.1")
            classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.stability.analyzer) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.owasp.dependency.check) apply false
    alias(libs.plugins.sonarqube)
}

val sonarProjectProperties =
    java.util.Properties().apply {
        rootProject.file("sonar-project.properties").inputStream().use(::load)
    }

sonar {
    properties {
        sonarProjectProperties.forEach { key, value -> property(key.toString(), value.toString()) }
    }
}

project(":app") {
    sonar {
        properties {
            property(
                "sonar.coverage.jacoco.xmlReportPaths",
                layout.buildDirectory.file("reports/coverage/sonar/report.xml").get().asFile.absolutePath,
            )
        }
    }
}

tasks.named("sonar") {
    dependsOn(":app:assembleDebug", ":app:sonarCoverageReport")
}
