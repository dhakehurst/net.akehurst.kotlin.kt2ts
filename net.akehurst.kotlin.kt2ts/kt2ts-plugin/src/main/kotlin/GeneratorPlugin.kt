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
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinUsages
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsSetupTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnSetupTask
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnSimple

class GeneratorPlugin : Plugin<ProjectInternal> {

    companion object {
        val TASK_NODE_BUILD = "nodeBuild"
    }

    override fun apply(project: ProjectInternal) {
        project.pluginManager.apply(BasePlugin::class.java)
        val ext = project.extensions.create<GeneratorPluginExtension>(GeneratorPluginExtension.NAME, GeneratorPluginExtension::class.java, project)

        val nodeKotlinConfig = project.configurations.create(ext.nodeConfigurationName.get()) {
            it.attributes {
                it.attribute(KotlinPlatformType.attribute, KotlinPlatformType.js)
                it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, KotlinUsages.KOTLIN_RUNTIME))
            }
        }

        project.gradle.projectsEvaluated {
            if (ext.nodeSrcDirectory.isPresent) {
                val nodeSrcDir = project.file(ext.nodeSrcDirectory.get())
                project.tasks.create(NodeJsSetupTask.NAME, NodeJsSetupTask::class.java) {
                    it.group = "nodejs"
                }
                project.tasks.create(YarnSetupTask.NAME, YarnSetupTask::class.java) {
                    it.group = "nodejs"
                    if (it.destination.exists()) {
                        // assume that already downloaded
                    } else {
                        it.setup()
                    }
                }
                // use yarn to install the node_modules required by the node code
                project.tasks.create("yarnInstall") {
                    it.group = "nodejs"
                    it.dependsOn(NodeJsSetupTask.NAME, YarnSetupTask.NAME)
                    it.doLast {
                        YarnSimple.yarnExec(project.rootProject, nodeSrcDir, "yarn install/add", "add", "--cwd", "--no-bin-links")
                    }
                }
                project.tasks.create(UnpackJsModulesTask.NAME, UnpackJsModulesTask::class.java) { tsk ->
                    tsk.dependsOn(nodeKotlinConfig, "yarnInstall")
                    tsk.moduleNameMap.set(ext.moduleNameMap)
                    tsk.nodeModulesDirectory.set(ext.nodeModulesDirectory)
                    tsk.unpackConfigurationName.set(ext.nodeConfigurationName)
                    tsk.excludeModules.set(ext.excludeModules)
                }
                project.tasks.create(AddKotlinStdlibDeclarationsTask.NAME, AddKotlinStdlibDeclarationsTask::class.java) { tsk ->
                    tsk.outputDirectory.set(ext.kotlinStdlibJsDirectory)
                }
                project.tasks.create(GeneratePackageJsonTask.NAME+"-kotlin", GeneratePackageJsonTask::class.java) { tsk ->
                    tsk.dependsOn(AddKotlinStdlibDeclarationsTask.NAME)
                    tsk.packageJsonDir.set(ext.kotlinStdlibJsDirectory)
                    tsk.moduleGroup.set("org.jetbrains.kotlin")
                    tsk.moduleName.set("kotlin-stdlib-js")
                }
                project.tasks.create(TASK_NODE_BUILD) {
                    it.group = "nodejs"
                    it.dependsOn(UnpackJsModulesTask.NAME)
                    it.dependsOn(GeneratePackageJsonTask.NAME+"-kotlin")
                    it.doLast {
                        val nodeArgs = ext.nodeBuildCommand.get().toTypedArray()
                        YarnSimple.yarnExec(project.rootProject, project.file(ext.nodeSrcDirectory.get()), "node build command", *nodeArgs)
                    }
                }

                project.tasks.getByName("${ext.jsTargetName.get()}ProcessResources").dependsOn(TASK_NODE_BUILD)

                if (ext.dynamicImport.isPresent && ext.dynamicImport.get().isNotEmpty()) {
                    project.tasks.create(GenerateDynamicRequire.NAME, GenerateDynamicRequire::class.java) { tsk ->
                        tsk.group = "generate"
                        tsk.dependsOn(UnpackJsModulesTask.NAME)
                        tsk.nodeModulesDirectory.set(ext.nodeModulesDirectory)
                        tsk.dynamicImport.set(ext.dynamicImport)
                    }
                    project.tasks.getByName(TASK_NODE_BUILD).dependsOn(GenerateDynamicRequire.NAME)
                }
            }
            ext.generateThirdPartyModules.forEach { cfg ->
                //TODO: '${cfg.moduleName.get()}' might not be unque!
                project.tasks.create("generateDeclarationsFor_${cfg.moduleName.get()}", GenerateDeclarationsTask::class.java) { tsk ->
                    tsk.group = "generate"
                    tsk.dependsOn(UnpackJsModulesTask.NAME)
                    tsk.jvmTargetName.set(ext.jvmTargetName)
                    tsk.jsTargetName.set(ext.jsTargetName)
                    tsk.moduleGroup.set(cfg.moduleGroup)
                    tsk.moduleName.set(cfg.moduleName)
                    tsk.classPatterns.set(cfg.classPatterns)
                    tsk.localOnly.set(false)
                    tsk.includeOnly.set(cfg.includeOnly)
                    tsk.declarationsFile.set(ext.nodeModulesDirectory.flatMap {
                        it.dir(cfg.tgtName).map { it.file("${tsk.moduleGroup.get()}-${tsk.moduleName.get()}-js.d.ts") }
                    })
                }
                project.tasks.create("ensurePackageJsonFor_${cfg.moduleName.get()}", GeneratePackageJsonTask::class.java) { tsk ->
                    tsk.group = "generate"
                    tsk.dependsOn("generateDeclarationsFor_${cfg.moduleName.get()}")
                    tsk.packageJsonDir.set(ext.nodeModulesDirectory.flatMap {
                        it.dir(cfg.tgtName)
                    })
                    tsk.moduleGAV.set(cfg.name)
                    tsk.mainFileName.set(cfg.mainFileName)
                }
            }

            if (ext.classPatterns.isPresent && ext.classPatterns.get().isNotEmpty()) {
                // generate own .d.ts
                project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.sourceSets?.findByName(ext.jsTargetName.map { "${it}Main" }.get())?.resources?.srcDir(ext.tsdOutputDirectory)

                project.tasks.create(GeneratePackageJsonTask.NAME, GeneratePackageJsonTask::class.java) { tsk ->
                    tsk.packageJsonDir.set(ext.tsdOutputDirectory)
                }
                project.tasks.create(GenerateDeclarationsTask.NAME, GenerateDeclarationsTask::class.java) { gt ->
                    //TODO: how to set dependsOn to "${ext.jvmName}MainClasses" ?
                    val jvmMainClasses = ext.jvmTargetName.map { "${it}MainClasses" }
                    gt.dependsOn(GeneratePackageJsonTask.NAME, jvmMainClasses)
                    gt.overwrite.set(ext.overwrite)
                    gt.localOnly.set(ext.localOnly)
                    gt.includeOnly.set(ext.includeOnly)
                    gt.declarationsFile.set(ext.declarationsFile)
                    gt.jvmTargetName.set(ext.jvmTargetName)
                    gt.jsTargetName.set(ext.jsTargetName)
                    gt.classPatterns.set(ext.classPatterns)
                    gt.typeMapping.set(ext.typeMapping)
                    gt.outputDirectory.set(ext.tsdOutputDirectory)
                    gt.declarationsFile.set(ext.declarationsFile)
                    //gt.dependencies = ext.dependencies
                    gt.moduleNameMap.set(ext.moduleNameMap)
                }

                project.tasks.getByName("jsJar").dependsOn(GenerateDeclarationsTask.NAME)
            }
        }
    }

}
