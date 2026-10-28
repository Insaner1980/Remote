import com.android.build.gradle.internal.coverage.JacocoReportTask
import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    jacoco
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.stability.analyzer)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.owasp.dependency.check)
}

android {
    namespace = "com.emmah.colordock.remote"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.emmah.colordock.remote"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = false
            // AGP's Compose Preview tasks require resources even during task discovery.
            isIncludeAndroidResources = true
        }
    }
    testCoverage { jacocoVersion = "0.8.15" }
    sourceSets {
        getByName("test").kotlin.directories.add("src/sharedTest/java")
        getByName("androidTest").kotlin.directories.add("src/sharedTest/java")
    }
}

kotlin { jvmToolchain(17) }

jacoco { toolVersion = "0.8.15" }

tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        },
    )
    maxHeapSize = "2g"
    // Robolectric accesses Java's file descriptor bridge.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--enable-native-access=ALL-UNNAMED")
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("sonarCoverageReport") {
    group = "verification"
    description = "Reports JVM test coverage only; does not use or run device tests."
    val unitCoverage = tasks.named<JacocoReportTask>("createDebugUnitTestCoverageReport")
    dependsOn(unitCoverage)
    jacocoClasspath = unitCoverage.get().jacocoClasspath
    classDirectories.from(unitCoverage.map { it.classFileCollection })
    sourceDirectories.from(files("src/main/java", "src/debug/java"))
    executionData.from(unitCoverage.map { it.coverageFiles.asFileTree })
    doFirst {
        logger.lifecycle("Sonar: JVM test coverage only; no emulator, device tests or saved device coverage.")
    }
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/coverage/sonar/report.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/coverage/sonar/html"))
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
    coloredOutput.set(false)
    ignoreFailures.set(false)
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
    source.setFrom("src/main/java", "src/debug/java", "src/test/java", "src/androidTest/java", "src/sharedTest/java")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        xml.required.set(true)
        html.required.set(true)
        sarif.required.set(true)
    }
}

dependencyCheck {
    formats = listOf("HTML", "JSON")
    suppressionFiles = listOf(rootProject.file("config/dependency-check-suppressions.xml").absolutePath)
    outputDirectory = rootProject.layout.projectDirectory.dir("reports")
    scanConfigurations = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
    failBuildOnCVSS = 7f
    failOnError = true
    autoUpdate =
        providers
            .environmentVariable("DEPENDENCY_CHECK_AUTO_UPDATE")
            .map { it.lowercase() in listOf("1", "true", "yes", "on") }
            .getOrElse(true)
    analyzers {
        ossIndex {
            enabled = false
        }
    }
    nvd {
        providers
            .environmentVariable("NVD_API_KEY")
            .orNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { apiKey = it }
    }
}

// Dependency-Check 13 requires the cache directory to be set on its tasks.
tasks.withType<org.owasp.dependencycheck.gradle.tasks.ConfiguredTask>().configureEach {
    data.directory.set(
        providers
            .environmentVariable("DEPENDENCY_CHECK_DATA_DIRECTORY")
            .orElse(
                rootProject.layout.projectDirectory
                    .dir(".gradle/dependency-check-data")
                    .asFile.absolutePath,
            ),
    )
}

dependencies {
    lintChecks(libs.android.security.lints)
    detektPlugins(libs.compose.rules.detekt)
    ktlintRuleset(libs.compose.rules.ktlint)

    constraints {
        // Ktlint still requests Logback 1.3; keep its backend and core on the same patched release.
        add("ktlint", "ch.qos.logback:logback-classic:1.6.6")
        add("ktlint", "ch.qos.logback:logback-core:1.6.6")
        // Lint resolves its own copy of AGP's transitive tools.
        add("androidLintTool", "com.google.guava:guava:33.7.2-jre")
        add("androidLintTool", "org.apache.commons:commons-lang3:3.21.0")
        add("androidLintTool", "org.apache.httpcomponents:httpclient:4.5.14")
        add("androidLintTool", "org.bouncycastle:bcpkix-jdk18on:1.86")
        add("androidLintTool", "org.bouncycastle:bcprov-jdk18on:1.86")
    }

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.datastore)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.test.ext)
    testImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext)
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
