plugins { `java-library` }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

dependencyLocking { lockAllConfigurations() }

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed", "skipped") }
}

val verifyCoreBoundaries by tasks.registering {
    group = "verification"
    description = "Reject runtime dependencies and bytecode dependencies outside java.base."
    dependsOn(tasks.classes)
    doLast {
        check(configurations.runtimeClasspath.get().files.isEmpty()) {
            "The core must have no runtime dependencies on clients, drivers, or third-party libraries."
        }
        val launcher = javaToolchains.launcherFor(java.toolchain).get()
        val executable = launcher.metadata.installationPath.file(
            "bin/" + if (System.getProperty("os.name").startsWith("Windows")) "jdeps.exe" else "jdeps"
        ).asFile.absolutePath
        val classes = sourceSets.main.get().output.classesDirs.files.filter { it.exists() }
        check(classes.isNotEmpty()) { "No compiled core classes available to verify." }
        val output = providers.exec {
            commandLine(listOf(executable, "-s", "--recursive") + classes.map { it.absolutePath })
        }.standardOutput.asText.get()
        val dependencies = output.lineSequence().filter { " -> " in it }.toList()
        check(dependencies.isNotEmpty()) { "jdeps produced no dependency report." }
        check(dependencies.all { it.substringAfter(" -> ").trim() == "java.base" }) {
            "Core bytecode may depend only on java.base at this stage:\n$output"
        }
    }
}

tasks.named("check") { dependsOn(verifyCoreBoundaries) }
