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

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory

open class GeneratorPluginExtension(project: Project, objects: ObjectFactory) {

    companion object {
        val NAME = "kt2ts"
    }

    /**
     * directory where the Node.js code is perhaps "${project.projectDir}/src/node"
     * when this value is set, the node build tasks are created
     */
    val nodeSrcDirectory = objects.directoryProperty()

    val nodeBuildCommand = objects.listProperty(String::class.java)

    /**
     * directory where built code is output default [${project.buildDir}/node]
     */
    val nodeOutDirectory = objects.directoryProperty()

    /**
     * name of the configuration to use for finding depended modules [default 'jvmRuntimeClasspath']
     */
    val nodeConfigurationName = objects.property(String::class.java)

    val nodeModulesDirectory = objects.directoryProperty()

    val kotlinStdlibJsDirectory = objects.directoryProperty()

    /**
     * list of modules that are imported/required dynamically.
     * i.e. those that have been registered for import vi 'net.akehurst.kotlinx.reflect.ModuleRegistry.register'
     */
    val dynamicImport = objects.listProperty(String::class.java)

    /**
     * name of the configuration to use for unpacking
     */
    // val unpackConfigurationName = objects.property(String::class.java)


    val generateThirdPartyModules = objects.domainObjectContainer(GenerateThirdPartyModuleConfig::class.java)

    //--- configuration for generating tsd on self

    /**
     * name of the directory into which the files are generated [{buildDir}/tmp/jsJar/ts]
     */
    val tsdOutputDirectory = objects.directoryProperty()

    /**
     * overwrite declaration file if it already exists [default true]
     */
    val overwrite = objects.property(Boolean::class.java) //TODO: not sure this is useful!

    /**
     * use only the classes defined in the local project (not any from dependencies) [default true]
     */
    val localOnly = objects.property(Boolean::class.java)

    /**
     * if not empty, only use classes from this module
     */
    val includeOnly = objects.listProperty(String::class.java)

    /**
     * name of the kotlin jvm target for this module [default 'jvm']
     */
    val jvmTargetName = objects.property(String::class.java)

    /**
     * name of the kotlin js target for this module [default 'js']
     */
    val jsTargetName = objects.property(String::class.java)

    val declarationsFile = objects.fileProperty()
    val classPatterns = objects.listProperty(String::class.java)
    val typeMapping = objects.mapProperty(String::class.java, String::class.java)
    //var dependencies = objects.listProperty(String::class.java)
    val moduleNameMap = objects.mapProperty(String::class.java, String::class.java)

    /**
     * modules to exclude, do not unpack
     */
    val excludeModules = objects.listProperty(String::class.java)


    init {
        // node build configuration
        this.nodeConfigurationName.convention("nodeKotlin")
        this.nodeOutDirectory.convention( project.layout.buildDirectory.dir("node") )
        this.nodeModulesDirectory.convention(this.nodeSrcDirectory.map { it.dir("node_modules") })
        this.kotlinStdlibJsDirectory.convention(this.nodeModulesDirectory.map { it.dir("kotlin") })
        this.nodeBuildCommand.convention(listOf("run", "build", "--outputPath=${this.nodeOutDirectory.get()}/dist"))

        val tsOutDir = project.layout.buildDirectory.dir("tmp/jsJar/ts")
        this.tsdOutputDirectory.convention(tsOutDir)
        this.overwrite.convention(true)
        this.localOnly.convention(true)
        this.jvmTargetName.convention("jvm")
        this.jsTargetName.convention("js")

        this.declarationsFile.convention(tsdOutputDirectory.file("${project.group}-${project.name}.d.ts"))
        this.excludeModules.convention(listOf(
                "org.jetbrains.kotlin:kotlin-stdlib",
                "org.jetbrains.kotlin:kotlin-stdlib-common",
                "org.jetbrains.kotlin:kotlin-stdlib-js",
                "org.jetbrains:annotations",
                "org.jetbrains.kotlin:kotlin-reflect"
        ))
        this.typeMapping.convention(mapOf(
                "kotlin.reflect.KClass" to "any", //not sure what else to use!
                "kotlin.Unit" to "void",
                "kotlin.Any" to "any",
                "kotlin.Array" to "Array",
                "kotlin.CharSequence" to "string",
                "kotlin.Char" to "number",
                "kotlin.String" to "string",
                "kotlin.Number" to "number",
                "kotlin.Byte" to "number",
                "kotlin.Short" to "number",
                "kotlin.Int" to "number",
                "kotlin.Float" to "number",
                "kotlin.Double" to "number",
                "kotlin.Boolean" to "boolean",
                "kotlin.Throwable" to "Error",
                "kotlin.Exception" to "Error",
                "kotlin.RuntimeException" to "Error",
                "java.lang.Exception" to "Error",
                "java.lang.RuntimeException" to "Error"
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

}

open class GenerateThirdPartyModuleConfig constructor(val name: String, objects: ObjectFactory) {

    val moduleGAV = objects.property(String::class.java)

    /**
     * if not empty, only use classes from this module
     */
    val includeOnly = objects.listProperty(String::class.java)

    val moduleGroup = objects.property(String::class.java)
    val moduleName = objects.property(String::class.java)
    val moduleVersion = objects.property(String::class.java)
    val mainFileName = objects.property(String::class.java)
    val tgtName = objects.property(String::class.java)
    val classPatterns = objects.listProperty(String::class.java)

    init {
        this.moduleGAV.convention(this.name)
        this.moduleGroup.convention(moduleGAV.map { it.split(':')[0] })
        this.moduleName.convention(moduleGAV.map { it.split(':')[1] })
        this.moduleVersion.convention(moduleGAV.map { it.split(':')[2] })
    }

}