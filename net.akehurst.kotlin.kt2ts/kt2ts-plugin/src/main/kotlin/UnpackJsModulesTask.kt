/**
 * Copyright (C) 2018 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.kotlin.kt2ts.plugin.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.slf4j.LoggerFactory

open class UnpackJsModulesTask : DefaultTask() {

    companion object {
        val NAME = "unpack_kotlin_js"
        private val LOGGER = LoggerFactory.getLogger(UnpackJsModulesTask::class.java)
    }

    @get:Input
    val unpackConfigurationName = project.objects.property(String::class.java)

    @get:Input
    val nodeModulesDirectory = project.objects.directoryProperty()

    @get:Input
    @get:Optional
    val moduleNameMap = project.objects.mapProperty(String::class.java, String::class.java)

    @get:Input
    val excludeModules = project.objects.listProperty(String::class.java)

    init {
        this.group = "angular"
        this.description = "Unpack Kotlin JS modules"
        this.unpackConfigurationName.convention("js")
        this.excludeModules.convention(listOf(
                "org.jetbrains.kotlin:kotlin-stdlib",
                "org.jetbrains.kotlin:kotlin-stdlib-common",
                "org.jetbrains.kotlin:kotlin-stdlib-js",
                "org.jetbrains:annotations",
                "org.jetbrains.kotlin:kotlin-reflect"
        ))
        this.moduleNameMap.convention(mapOf(
                "org.jetbrains.kotlinx:kotlinx-coroutines-core-js" to "kotlinx-coroutines-core",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core" to "kotlinx-coroutines-core",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core-common" to "kotlinx-coroutines-core-common",
                "org.jetbrains.kotlinx:kotlinx-coroutines-io-js" to "kotlinx-io-kotlinx-coroutines-io",
                "org.jetbrains.kotlinx:kotlinx-coroutines-io" to "kotlinx-io-kotlinx-coroutines-io",
                "org.jetbrains.kotlinx:kotlinx-io-js" to "kotlinx-io",
                "org.jetbrains.kotlinx:kotlinx-io" to "kotlinx-io",
                "org.jetbrains.kotlinx:atomicfu-common" to "kotlinx-atomicfu",
                "org.jetbrains.kotlinx:atomicfu-js" to "kotlinx-atomicfu",
                "org.jetbrains.kotlinx:atomicfu" to "kotlinx-atomicfu",
                "io.ktor:ktor-http-cio-js" to "ktor-ktor-http-cio",
                "io.ktor:ktor-http-cio" to "ktor-ktor-http-cio",
                "io.ktor:ktor-client-core-js" to "ktor-ktor-client-core",
                "io.ktor:ktor-client-core" to "ktor-ktor-client-core",
                "io.ktor:ktor-client-websockets-js" to "ktor-ktor-client-websockets",
                "io.ktor:ktor-client-websockets" to "ktor-ktor-client-websockets",
                "io.ktor:ktor-http-js" to "ktor-ktor-http",
                "io.ktor:ktor-http" to "ktor-ktor-http",
                "io.ktor:ktor-utils-js" to "ktor-ktor-utils",
                "io.ktor:ktor-utils" to "ktor-ktor-utils"
        ))
    }

    @TaskAction
    internal fun exec() {
        val cnfName = unpackConfigurationName.get()
        val cnf = this.project.configurations.findByName(cnfName) ?: throw RuntimeException("Cannot find $cnfName configuration")
        cnf.resolvedConfiguration.resolvedArtifacts.forEach { dep ->
            if (this.excludeModules.get().contains("${dep.moduleVersion.id.group}:${dep.name}")) {
                // do not unpack
            } else {
                LOGGER.debug("unpacking ${dep.name}")
                val dn = dep.name.substringBeforeLast("-")
                val tgtName = moduleNameMap.get().get("${dep.moduleVersion.id.group}:${dep.name}") ?: "${dep.moduleVersion.id.group}-${dn}"
                project.copy {
                    it.from(project.zipTree(dep.file)) {
                        it.includeEmptyDirs = false
                        it.include { fileTreeElement ->
                            val path = fileTreeElement.path
                            (path.endsWith(".js") || path.endsWith("d.ts") || path.endsWith("package.json"))
                                    && (path.startsWith("META-INF/resources/") || !path.startsWith("META-INF/"))
                        }
                        it.into(tgtName)
                    }
                    it.into(nodeModulesDirectory.get())
                }
                val packageJsonFile = nodeModulesDirectory.file("$tgtName/package.json").get().asFile
                if (packageJsonFile.exists()) {
                    //do nothing
                } else {
                    val moduleName = tgtName
                    val moduleVersion = dep.moduleVersion.id.version
                    val mainFileName = "$tgtName.js"
                    GeneratePackageJsonTask.readOrCreatePackageJson(packageJsonFile, moduleName, moduleVersion, mainFileName)
                }
            }
        }
    }
}
