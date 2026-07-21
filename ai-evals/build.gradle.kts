plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(project(":core:assistant-contract"))
    testImplementation("dev.dokimos:dokimos-junit:0.24.0")
    testImplementation(libs.gson)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.1")
}

tasks.test {
    useJUnitPlatform()
    providers.gradleProperty("menupilotModelRuns").orNull?.let { modelRunsPath ->
        systemProperty("menupilot.modelRuns", modelRunsPath)
    }
    providers.gradleProperty("menupilotFunctionGemmaRuns").orNull?.let { modelRunsPath ->
        systemProperty("menupilot.functionGemmaRuns", modelRunsPath)
    }
}
