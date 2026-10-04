plugins { `java-library`; `java-test-fixtures` }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
dependencyLocking { lockAllConfigurations() }
dependencies {
    api(project(":datacraft-core"))
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.10")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    testFixturesImplementation("org.testcontainers:testcontainers:2.0.5")
}
tasks.test { useJUnitPlatform { excludeTags("integration") } }
tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Verify both MySQL and MariaDB in disposable containers; Docker required."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
    outputs.upToDateWhen { false }
}
