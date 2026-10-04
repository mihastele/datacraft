plugins { application }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
dependencyLocking { lockAllConfigurations() }

val os = System.getProperty("os.name").lowercase()
val arm = System.getProperty("os.arch") in listOf("aarch64", "arm64")
val fxPlatform = when {
    os.startsWith("windows") && !arm -> "win"
    os.startsWith("linux") -> if (arm) "linux-aarch64" else "linux"
    os.startsWith("mac") -> if (arm) "mac-aarch64" else "mac"
    else -> error("Unsupported JavaFX desktop platform: $os")
}

dependencies {
    implementation(project(":datacraft-core"))
    implementation(project(":datacraft-sql"))
    implementation(project(":datacraft-platform"))
    implementation(project(":datacraft-postgresql"))
    implementation(project(":datacraft-sqlite"))
    implementation(project(":datacraft-mysql"))
    testImplementation(testFixtures(project(":datacraft-mysql")))
    for (module in listOf("base", "graphics", "controls")) {
        implementation("org.openjfx:javafx-$module:21.0.12:$fxPlatform") { isTransitive = false }
    }
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
}

application {
    mainClass.set("io.datacraft.desktop.Launcher")
    applicationName = "datacraft"
}

distributions {
    main {
        contents {
            from(rootProject.file("LICENSE"))
            from(rootProject.file("docs/licenses")) { into("licenses") }
        }
    }
}

tasks.test { useJUnitPlatform { excludeTags("desktop") } }
tasks.register<Test>("desktopTest") {
    group = "verification"
    description = "Verify JavaFX workflows against PostgreSQL, SQLite, MySQL and MariaDB; needs Docker and a display."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("desktop") }
    shouldRunAfter(tasks.test)
    outputs.upToDateWhen { false }
}
