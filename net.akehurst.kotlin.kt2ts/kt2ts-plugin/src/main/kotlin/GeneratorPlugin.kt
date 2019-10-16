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

import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin

class GeneratorPlugin : Plugin<ProjectInternal> {

    override fun apply(project: ProjectInternal) {
        project.pluginManager.apply(BasePlugin::class.java)

        val ext = project.extensions.create<GeneratorPluginExtension>(GeneratorPluginExtension.NAME, GeneratorPluginExtension::class.java)
        project.tasks.create(GenerateTask.NAME, GenerateTask::class.java) { gt ->
            gt.doLast {
                println("outputFile ${ext.outputFile}")
                gt.outputFile = ext.outputFile
                gt.templateDir = ext.templateDir
                gt.configurationName = ext.configurationName
                gt.classPatterns = ext.classPatterns
                gt.typeMapping = ext.typeMapping
            }
        }

    }

}
