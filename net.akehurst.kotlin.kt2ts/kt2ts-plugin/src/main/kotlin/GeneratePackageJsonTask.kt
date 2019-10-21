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

import groovy.json.JsonSlurper
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.stringify
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.slf4j.LoggerFactory
import java.io.File

open class GeneratePackageJsonTask : DefaultTask() {

    companion object {
        val NAME = "generatePackageJsonWithTypes"
        private val LOGGER = LoggerFactory.getLogger(GeneratePackageJsonTask::class.java)

        fun execute(_packageJsonFile: File, _moduleGroup: String, _moduleName: String, _moduleVersion: String) {
            println("$_packageJsonFile, $_moduleName, $_moduleVersion")
            val file = _packageJsonFile
            val mainFileName = if (_moduleName.endsWith("-js")) _moduleName.substringBeforeLast("-js") else _moduleName
            val json = readOrCreatePackageJson(file, _moduleName, _moduleVersion, "$_moduleGroup-$mainFileName")
            val mutable = mutableMapOf<String, JsonElement>()
            mutable.putAll(json)
            mutable["types"] = JsonLiteral("./${_moduleGroup}-${_moduleName}.d.ts")
            val newJson = JsonObject(mutable)

            file.printWriter().use { out ->
                out.println(Json.indented.stringify(JsonObjectSerializer, newJson))
            }
        }

        private fun readOrCreatePackageJson(file: File, _moduleName: String, _moduleVersion: String, mainFileName:String): JsonObject {
            val json = Json(JsonConfiguration.Stable)
            return if (file.exists()) {
                val json = Json(JsonConfiguration.Stable)
                json.parseJson(file.readText()).jsonObject
            } else {
                LOGGER.info("Creating new file $file")
                file.printWriter().use { out ->
                    out.println("""
                {
                    "name": "${_moduleName}",
                    "version": "${_moduleVersion}",
                    "main": "./${mainFileName}.js"
                }
                """.trimIndent())
                }
                json.parseJson(file.readText()).jsonObject
            }
        }
    }

    @get:OutputFile
    var packageJsonFile = project.objects.fileProperty()

    @get:Input
    var moduleName = project.objects.property(String::class.java)

    @get:Input
    var moduleGroup = project.objects.property(String::class.java)

    @get:Input
    var moduleVersion = project.objects.property(String::class.java)

    init {
        this.group = "generate"
        this.description = "Generate package.json file that defines 'types', or modify existing package.json file to add it"
        this.moduleName.set(project.name)
        this.moduleGroup.set( project.group as String )
        this.moduleVersion.set(project.version as String)
    }

    @TaskAction
    internal fun exec() {
        LOGGER.info("Executing $NAME")
        execute(packageJsonFile.get().asFile, moduleGroup.get(), moduleName.get(), moduleVersion.get())
    }

}
