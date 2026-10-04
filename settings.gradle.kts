rootProject.name = "datacraft"
include("datacraft-core", "datacraft-postgresql", "datacraft-sqlite", "datacraft-mysql", "datacraft-desktop", "datacraft-platform")
include("datacraft-sql")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}
