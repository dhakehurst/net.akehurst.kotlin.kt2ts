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
        val ext = project.extensions.create<GeneratorPluginExtension>(GeneratorPluginExtension.NAME, GeneratorPluginExtension::class.java, project)
        project.tasks.create(GeneratePackageJsonTask.NAME, GeneratePackageJsonTask::class.java) { tsk ->
            tsk.packageJsonDir.set(ext.outputDirectory)
        }
        project.tasks.create(GenerateDeclarationsTask.NAME, GenerateDeclarationsTask::class.java) { gt ->
            //TODO: how to set dependsOn to "${ext.jvmName}MainClasses" ?
            gt.dependsOn(GeneratePackageJsonTask.NAME)
            gt.overwrite.set(ext.overwrite)
            gt.localOnly.set(ext.localOnly)
            gt.moduleOnly.set(ext.moduleOnly)
            gt.declarationsFile.set(ext.declarationsFile)
            gt.localJvmName.set(ext.localJvmName)
            gt.modulesConfigurationName.set(ext.modulesConfigurationName)
            gt.classPatterns.set(ext.classPatterns)
            gt.typeMapping.set(ext.typeMapping)
            gt.outputDirectory.set(ext.outputDirectory)
            gt.declarationsFile.set(ext.declarationsFile)
            //gt.dependencies = ext.dependencies
            gt.moduleNameMap.set(ext.moduleNameMap)
        }
        project.tasks.create(AddKotlinStdlibDeclarationsTask.NAME, AddKotlinStdlibDeclarationsTask::class.java) { tsk ->
            tsk.outputDir.set(ext.kotlinStdlibJsDir)
        }
        project.tasks.create(UnpackJsModulesTask.NAME, UnpackJsModulesTask::class.java) { tsk ->
            tsk.moduleNameMap.set(ext.moduleNameMap)
            tsk.nodeModulesDirectoryPath.set(ext.nodeModulesDirectoryPath)
            tsk.unpackConfigurationName.set(ext.unpackConfigurationName)
            tsk.excludeModules.set(ext.excludeModules)
        }
    }

}
