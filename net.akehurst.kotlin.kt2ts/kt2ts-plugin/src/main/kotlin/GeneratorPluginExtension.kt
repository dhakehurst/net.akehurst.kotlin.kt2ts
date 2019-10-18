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
import java.io.File

open class GeneratorPluginExtension(objects: ObjectFactory)  {

    companion object {
        val NAME = "kt2ts"
    }

    /**
     * overwrite declaration file if it already exists [default false]
     */
    var overwrite = objects.property(Boolean::class.java)

    /**
     * use only the classes defined in the local project (not any from dependencies) [default true]
     */
    var localOnly = objects.property(Boolean::class.java)

    /**
     * name of the jvm configuration [default 'jvm']
     */
    var jvmName = objects.property(String::class.java) //"commonMainImplementation"
    var templateFileName = objects.property(String::class.java) //"template.hbs"
    var templateDir = objects.directoryProperty()
    var outputFile = objects.fileProperty()
    var classPatterns = objects.listProperty(String::class.java)
    var typeMapping = objects.mapProperty(String::class.java,String::class.java)

    init {
        this.overwrite.set(false)
        this.localOnly.set(true)
        this.jvmName.set("jvm")
        this.templateFileName.set("template.hbs")
        this.typeMapping.set(mapOf(
                "kotlin.Any" to "any",
                "kotlin.String" to "string",
                "kotlin.Int" to "number",
                "kotlin.Float" to "number",
                "kotlin.Double" to "number",
                "kotlin.Boolean" to "boolean"
        ))
    }



}