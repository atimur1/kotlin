package org.jetbrains.kotlin.pill

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class JpsCompatiblePlugin : Plugin<Project> {
    override fun apply(project: Project) {}
}

class JpsCompatibleRootPlugin : Plugin<Project> {
    companion object {
        private fun mapper(module: String, vararg configurations: String): DependencyMapper {
            return DependencyMapper("org.jetbrains.kotlin", module, *configurations) { MappedDependency(PDependency.Library(module)) }
        }

        private val dependencyMappers = listOf(
            mapper("protobuf-relocated", "default"),
            mapper("kotlin-test-junit", "distJar", "runtimeElements"),
            mapper("kotlin-script-runtime", "distJar", "runtimeElements"),
            mapper("kotlin-reflect", "distJar", "runtimeElements"),
            mapper("kotlin-test-jvm", "distJar", "runtimeElements"),
            DependencyMapper("org.jetbrains.kotlin", "kotlin-stdlib", "distJar", "runtimeElements") {
                MappedDependency(
                    PDependency.Library("kotlin-stdlib"),
                    listOf(PDependency.Library("annotations-13.0"))
                )
            },
            DependencyMapper("org.jetbrains.kotlin", "kotlin-reflect-api", "runtimeElements") {
                MappedDependency(PDependency.Library("kotlin-reflect"))
            },
            DependencyMapper("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "runtimeJar") { null },
            DependencyMapper("org.jetbrains.kotlin", "kotlin-stdlib-js", "distJar") { null },
            DependencyMapper("org.jetbrains.kotlin", "kotlin-compiler", "runtimeJar") { null },
            DependencyMapper("org.jetbrains.kotlin", "compiler", "runtimeElements") { null }
        )
    }

    override fun apply(project: Project) {
        project.tasks.create("pill") {
            this.doLast { pill(project) }
        }
    }

    private fun pill(project: Project) {
        val platformVersion = project.rootProject.extensions.extraProperties.get("versions.intellijSdk").toString()

        val jpsProject = attachSources(parse(project, ParserContext(dependencyMappers)), platformVersion)
        val files = render(jpsProject, KotlinSpecific.getProjectLibraries(jpsProject))

        File(project.rootProject.projectDir, ".idea/libraries").deleteRecursively()

        for (file in files) {
            val stubFile = file.path
            stubFile.parentFile.mkdirs()
            stubFile.writeText(file.text)
        }
    }

    private fun attachSources(project: PProject, platformVersion: String): PProject {
        val platformDir = File(project.rootDirectory, "buildSrc/prepare-deps/intellij-sdk/build/repo/kotlin.build.custom.deps/$platformVersion")
        val platformSourcesJar = File(platformDir, "sources/ideaIC-$platformVersion-sources.jar")

        fun attachSources(root: POrderRoot): POrderRoot {
            val dependency = root.dependency

            if (dependency is PDependency.ModuleLibrary) {
                val library = dependency.library
                if (library.classes.any { it.startsWith(platformDir) }) {
                    val libraryWithSources = library.copy(sources = library.sources + listOf(platformSourcesJar))
                    return root.copy(dependency = dependency.copy(library = libraryWithSources))
                }
            }

            return root
        }

        val modules = project.modules.map { it.copy(orderRoots = it.orderRoots.map(::attachSources)) }
        return project.copy(modules = modules)
    }
}