package org.jetbrains.kotlin.pill

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class JpsCompatiblePlugin : Plugin<Project> {
    override fun apply(project: Project) {}
}

class JpsCompatibleRootPlugin : Plugin<Project> {
    companion object {
        private fun mapper(module: String, configuration: String): DependencyMapper {
            return DependencyMapper("org.jetbrains.kotlin", module, configuration) { PDependency.Library(module) }
        }

        private val dependencyMappers = listOf(
            mapper("protobuf-relocated", "default"),
            mapper("kotlin-test-junit", "distJar"),
            mapper("kotlin-test-junit", "runtimeElements"),
            mapper("kotlin-script-runtime", "distJar"),
            mapper("kotlin-script-runtime", "runtimeElements"),
            mapper("kotlin-reflect", "runtimeElements"),
            mapper("kotlin-reflect", "distJar"),
            mapper("kotlin-stdlib", "runtimeElements"),
            mapper("kotlin-stdlib", "distJar"),
            mapper("kotlin-test-jvm", "distJar"),
            mapper("kotlin-test-jvm", "runtimeElements"),
            DependencyMapper("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "runtimeJar") { null },
            DependencyMapper("org.jetbrains.kotlin", "kotlin-reflect-api", "runtimeElements") { PDependency.Library("kotlin-reflect") },
            DependencyMapper("org.jetbrains.kotlin", "kotlin-stdlib-js", "distJar") { null },
            DependencyMapper("org.jetbrains.kotlin", "kotlin-compiler", "runtimeJar") { null },
            DependencyMapper("org.jetbrains.kotlin", "compiler", "runtimeElements") { null }
        )
    }

    override fun apply(project: Project) {
        project.tasks.create("pill") {
            this.doLast {
                val jpsProject = KotlinSpecific.postProcess(parse(project, ParserContext(dependencyMappers)))
                val files = render(jpsProject, KotlinSpecific.getProjectLibraries(jpsProject))

                File(project.rootProject.projectDir, ".idea/libraries").deleteRecursively()

                for (file in files) {
                    val stubFile = file.path
                    stubFile.parentFile.mkdirs()
                    stubFile.writeText(file.text)
                }
            }
        }
    }


}