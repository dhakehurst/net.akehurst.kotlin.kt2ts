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
import org.gradle.api.tasks.Input
import java.io.File

open class GeneratorPluginExtension(project: Project, objects: ObjectFactory)  {

    companion object {
        val NAME = "kt2ts"
    }

    /**
     * name of the configuration to use for unpacking
     */
    var unpackConfigurationName = objects.property(String::class.java)

    val nodeModulesDirectoryPath = objects.property(String::class.java)

    /**
     * overwrite declaration file if it already exists [default false]
     */
    var overwrite = objects.property(Boolean::class.java)

    /**
     * use only the classes defined in the local project (not any from dependencies) [default true]
     */
    var localOnly = objects.property(Boolean::class.java)

    /**
     * if not empty, only use classes from this module
     */
    var moduleOnly = objects.listProperty(String::class.java)

    /**
     * name of the jvm configuration for this module (locally build classes) [default 'jvm']
     */
    var localJvmName = objects.property(String::class.java) //"commonMainImplementation"

    /**
     * name of the configuration to use for finding dependend modules
     */
    var modulesConfigurationName = objects.property(String::class.java)
    var outputDirectory = objects.directoryProperty()
    var declarationsFile = objects.fileProperty()
    var classPatterns = objects.listProperty(String::class.java)
    var typeMapping = objects.mapProperty(String::class.java,String::class.java)
    //var dependencies = objects.listProperty(String::class.java)
    var moduleNameMap = objects.mapProperty(String::class.java, String::class.java)

    /**
     * modules to exclude, do not unpack
     */
    var excludeModules = objects.listProperty(String::class.java)

    var kotlinStdlibJsDir = objects.directoryProperty()

    init {
        this.overwrite.convention(true)
        this.localOnly.convention(true)
        this.localJvmName.convention("jvm")
        this.modulesConfigurationName.convention("jvmRuntimeClasspath")
        val outDir = project.layout.buildDirectory.dir("tmp/jsJar/ts")
        this.outputDirectory.convention(outDir)
        this.declarationsFile.convention(outDir.get().file("${project.group}-${project.name}.d.ts"))
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

    }



}