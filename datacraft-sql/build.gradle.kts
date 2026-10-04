plugins { `java-library` }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
dependencyLocking { lockAllConfigurations() }
dependencies {
    api(project(":datacraft-core"))
    implementation("com.github.jsqlparser:jsqlparser:5.4")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.test { useJUnitPlatform() }
val coreJarName = "datacraft-core-${project.version}.jar"
val verifySqlBoundaries by tasks.registering {
    group = "verification"
    description = "Keep SQL intelligence independent of UI, JDBC adapters and native libraries."
    dependsOn(tasks.classes)
    doLast {
        val allowed = setOf(coreJarName, "jsqlparser-5.4.jar")
        check(configurations.runtimeClasspath.get().files.all { it.name in allowed }) {
            "SQL intelligence may depend only on core and the pinned parser."
        }
    }
}
tasks.named("check") { dependsOn(verifySqlBoundaries) }
