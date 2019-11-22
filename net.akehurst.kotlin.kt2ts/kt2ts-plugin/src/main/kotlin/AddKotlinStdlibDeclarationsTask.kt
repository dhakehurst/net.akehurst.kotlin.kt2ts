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

open class AddKotlinStdlibDeclarationsTask : DefaultTask() {

    companion object {
        val NAME = "addKotlinStdlibDeclarations"
        val KOTLIN_STDLIB_DECL_FILE_NAME = "org.jetbrains.kotlin-kotlin-stdlib-js.d.ts"
        val DEFAULT_KOTLIN_STDLIB = "/typescript/org.jetbrains.kotlin-kotlin-stdlib-js.d.ts"
        private val LOGGER = LoggerFactory.getLogger(AddKotlinStdlibDeclarationsTask::class.java)
    }

    @get:OutputDirectory
    var outputDir = project.objects.directoryProperty()

    init {
        this.group = "generate"
        this.description = "Add the typescript declaration file (*.d.ts) for kotlin-std-lib"
    }

    @TaskAction
    internal fun exec() {
        val text = this::class.java.getResource(DEFAULT_KOTLIN_STDLIB).readText()
        val declFile = outputDir.get().file(KOTLIN_STDLIB_DECL_FILE_NAME).asFile
        declFile.printWriter().use { out ->
            out.println(text)
        }
        val jsonFile = outputDir.get().file("package.json").asFile
        jsonFile.printWriter().use { out ->
            out.println("""
                {
                    "name": "kotlin-stdlib-js",
                    "main": "./kotlin.js",
                    "types" : "${KOTLIN_STDLIB_DECL_FILE_NAME}"
                }
                """.trimIndent())
        }
    }

}
