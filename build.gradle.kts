plugins { base }
tasks.named("check") { dependsOn(":datacraft-platform:check", ":datacraft-sql:check") }
tasks.named("assemble") { dependsOn(":datacraft-platform:assemble", ":datacraft-sql:assemble") }
tasks.named("clean") { dependsOn(":datacraft-platform:clean", ":datacraft-sql:clean") }

allprojects {
    group = "io.datacraft"
    version = "0.1.0-SNAPSHOT"
}

tasks.named("check") { dependsOn(":datacraft-core:check", ":datacraft-postgresql:check", ":datacraft-sqlite:check", ":datacraft-mysql:check", ":datacraft-desktop:check") }
tasks.named("assemble") { dependsOn(":datacraft-core:assemble", ":datacraft-postgresql:assemble", ":datacraft-sqlite:assemble", ":datacraft-mysql:assemble", ":datacraft-desktop:assemble") }
tasks.named("clean") { dependsOn(":datacraft-core:clean", ":datacraft-postgresql:clean", ":datacraft-sqlite:clean", ":datacraft-mysql:clean", ":datacraft-desktop:clean") }
tasks.register("integrationTest") {
    group = "verification"
    description = "Run real PostgreSQL, MySQL and MariaDB integration tests; requires Docker."
    dependsOn(":datacraft-postgresql:integrationTest", ":datacraft-mysql:integrationTest")
}
