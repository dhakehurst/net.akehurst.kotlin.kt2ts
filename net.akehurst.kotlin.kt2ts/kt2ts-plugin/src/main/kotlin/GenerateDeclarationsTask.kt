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

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.slf4j.LoggerFactory
import java.io.*
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import kotlin.Comparator
import kotlin.reflect.*
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.valueParameters
import kotlin.streams.toList

data class PackageData(
        val moduleName: String,
        val qualifiedName: String
)

data class Module(
        val group: String,
        val name: String
)

data class Namespace(
        val qualifiedName: String
) {
    lateinit var content: List<KClass<*>>
}

open class GenerateDeclarationsTask : DefaultTask() {

    companion object {
        val INDENT = "  "
        val NAME = "generateTypescriptDefinitionFile"
        private val LOGGER = LoggerFactory.getLogger(GenerateDeclarationsTask::class.java)
    }

    @get:Input
    var overwrite = project.objects.property(Boolean::class.java)

    @get:Input
    var localOnly = project.objects.property(Boolean::class.java)

    @get:Input
    var moduleOnly = project.objects.listProperty(String::class.java)

    @get:Input
    var localJvmName = project.objects.property(String::class.java)

    @get:Input
    var modulesConfigurationName = project.objects.property(String::class.java)

    @get:OutputFile
    var declarationsFile = project.objects.fileProperty()

    @get:Input
    var classPatterns = project.objects.listProperty(String::class.java)

    @get:Input
    @get:Optional
    var typeMapping = project.objects.mapProperty(String::class.java, String::class.java)

    @get:Input
    @get:Optional
    var dependencies = project.objects.listProperty(String::class.java)

    private var classModuleMap: MutableMap<String, String> = mutableMapOf()

    init {
        this.group = "generate"
        this.description = "Generate typescript declaration file (*.d.ts) from kotlin classes"

        this.overwrite.set(true)
        this.localOnly.set(true)
        this.localJvmName.set("jvm")
        this.modulesConfigurationName.set("jvmRuntimeClasspath")
        this.typeMapping.set(mapOf(
                "kotlin.reflect.KClass" to "any", //not sure what else to use!
                "kotlin.Unit" to "void",
                "kotlin.Any" to "any",
                "kotlin.String" to "string",
                "kotlin.CharSequence" to "string",
                "kotlin.Number" to "number",
                "kotlin.Int" to "number",
                "kotlin.Long" to "number",
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

    @TaskAction
    internal fun exec() {
        LOGGER.info("Executing $NAME")

        //val model = this.createModel(this.classPatterns.get())
        //val model2 = this.createModel2(this.classPatterns.get())
        val of = this.declarationsFile.get().asFile
        val imports = dependencies.get().map {
            val split = it.split(":")
            Module(split[0], split[1])
        }
        val namespaces = fetchNamespaces(this.classPatterns.get())
        val content = generateFileContent(imports, namespaces)
        this.generate(content, of)

    }

    private fun fetchNamespaces(classPatterns: List<String>): List<Namespace> {
        LOGGER.info("Scanning classpath")
        LOGGER.info("Including:")
        classPatterns.forEach {
            LOGGER.info("  ${it}")
        }

        createClassModuleMapping() //TODO: this is not very efficient, stuff is scanned more than once, here and further on

        val clToGenerate = this.createClassLoaderForDecls()
        val clForAll = this.createClassLoaderForAll()
        val packages = mutableListOf<String>()
        val classes = mutableListOf<String>()
        classPatterns.forEach { pat ->
            if (pat.endsWith(".*")) {
                packages.add(pat.substringBeforeLast(".*"))
            } else {
                classes.add(pat)
            }
        }
        val cg = ClassGraph()//
                .ignoreParentClassLoaders()
                .overrideClassLoaders(clToGenerate)//
                .enableClassInfo()//
                //.verbose()//
                //.enableExternalClasses() //
                //.enableAnnotationInfo() //
                //.enableSystemPackages()//
                .whitelistPackages(*packages.toTypedArray())
                .whitelistClasses(*classes.toTypedArray())

        val scan = cg.scan()
        val classInfo = scan.allClasses.flatMap {
            listOf(it) + it.innerClasses
        }.toSet()

        LOGGER.info("Building Datatype model for ${classInfo.size} types")

        val comparator = Comparator { c1: ClassInfo, c2: ClassInfo ->
            when {
                c1 == c2 -> 0
                c1.getSuperclasses().contains(c2) -> 1
                c2.getSuperclasses().contains(c1) -> -1
                else -> 0
            }
        }
        val filtered = classInfo.filter {
            try {
                val kclass: KClass<*> = clForAll.loadClass(it.name).kotlin
                kclass.isAbstract //this will throw exception if kotlin reflection not possible
                when {
                    kclass.isCompanion -> false
                    else -> true

                }
            } catch (ex: Throwable) {
                LOGGER.info("Skipping Class ${it.name} because kotlin reflection not supported on it")
                false
            }
        }

        val sorted = filtered.sortedWith(comparator)
        val namespaceGroups = sorted.groupBy {
            if (it.isInnerClass) {
                it.name.substringBeforeLast("$") //TODO: handle more than one level of nesting (maybe just replace $ with . here
            } else {
                it.packageName
            }
        }
        return namespaceGroups.map { (nsn, classes) ->
            val ns = Namespace(nsn)
            ns.content = classes.map { clForAll.loadClass(it.name).kotlin }
            ns
        }
    }

    private fun generate(fileContent: String, outputFile: File) {
        LOGGER.info("Generating ", outputFile)
        // final DefaultMustacheFactory mf = new DefaultMustacheFactory();
        if (outputFile.exists() && overwrite.get().not()) {
            LOGGER.info("Skipping $outputFile as it exists, set overwrite=true to overwrite the file")
        } else {
            if (outputFile.exists()) {
                LOGGER.info("Overwriting existing $outputFile, set overwrite=false to keep the orginal")
            }
            try {
                LOGGER.info("Generating output")
                FileWriter(outputFile).use {
                    it.write(fileContent)
                }
            } catch (e: Exception) {
                LOGGER.error("Unable to generate", e)
            }
        }
    }

    private fun generateFileContent(imports: List<Module>, namespaces: List<Namespace>): String {
        val importStr = imports.map { generateImport(it) }.joinToString("\n")
        val namespaceStr = namespaces.map { generateNamespace(it) }.joinToString("\n")

        return "$importStr\n\n$namespaceStr"
    }

    private fun generateImport(module:Module): String {
        val alias = module.name.substringBeforeLast("-").replace("-", "_")
        val group = module.group
        val name = module.name
        val file = "$group-$name"
        return "import * as $alias from '$file';"
    }

    private fun generateNamespace(namespace: Namespace): String {
        val name = namespace.qualifiedName
        val declarations = namespace.content.map { generateKClass(it) }.joinToString("\n").prependIndent(INDENT)
        return "declare namespace $name {\n$declarations\n}"
    }

    private fun generateKClass(kclass: KClass<*>): String {
        return when {
            kclass.java.isEnum -> generateEnum(kclass)
            else -> generateKClass1(kclass)
        }
    }

    private fun generateKClass1(kclass: KClass<*>): String {
        val pkgName = kclass.qualifiedName!!.substringBeforeLast('.')
        val moduleName = this.classModuleMap[kclass.qualifiedName!!] ?: "unknown"
        val packageData = PackageData(moduleName, pkgName)
        val name = kclass.simpleName!!
        val properties = kclass.declaredMemberProperties.filter{it.visibility==KVisibility.PUBLIC}.map { generateProperty(it, packageData) }.joinToString("\n").prependIndent(INDENT)
        val methods = kclass.declaredMemberFunctions.filter{it.visibility==KVisibility.PUBLIC}.map { generateMethod(it, packageData) }.joinToString("\n").prependIndent(INDENT)
        val typeParams = if (kclass.typeParameters.isEmpty()) {
            ""
        } else {
            val tp = kclass.typeParameters.map { it.name }.joinToString(",")
            "<$tp>"
        }
        val prefix = when {
            null != kclass.objectInstance -> "var $name:"
            kclass.isAbstract -> "abstract class $name$typeParams"
            kclass.java.isInterface -> "interface $name$typeParams"
            else -> "class $name$typeParams"
        }
        val extends = mutableListOf<String>()
        val implements = mutableListOf<String>()
        kclass.supertypes.forEach { stype ->
            val sclass = stype.classifier as KClass<*>
            if (sclass == Any::class) {
                //donothing
            } else {
                val type = this.generateType(stype, packageData)
                when {
                    kclass.java.isInterface -> extends.add(type)
                    sclass.java.isInterface -> implements.add(type)
                    else -> extends.add(type)
                }
            }
        }
        val extendsStr = if (extends.isEmpty()) "" else "extends ${extends.joinToString(",")}"
        val implementsStr = if (implements.isEmpty()) "" else "implements ${implements.joinToString(",")}"
        val res = "$prefix $extendsStr $implementsStr {\n$properties\n\n$methods\n}"
        return res
    }

    private fun generateProperty(property: KProperty<*>, owningTypePackage: PackageData): String {
        val name = property.name
        val type = generateType(property.returnType, owningTypePackage)
        return "$name: $type;"
    }

    private fun generateMethod(method: KFunction<*>, owningTypePackage: PackageData): String {
        val name = method.name
        val generic = if (method.typeParameters.isEmpty()) {
            ""
        } else {
            val tps = method.typeParameters.map { it.name }.joinToString(",")
            "<$tps>"
        }
        val parameters = method.valueParameters.mapIndexed { index: Int, it: KParameter -> generateParameter(it, index, owningTypePackage) }.joinToString(", ")
        val returnType = generateType(method.returnType, owningTypePackage)
        return "$name$generic($parameters): $returnType;"
    }

    private fun generateParameter(parameter: KParameter, index: Int, owningTypePackage: PackageData): String {
        val name = parameter.name ?: "arg$index"
        val type = generateType(parameter.type, owningTypePackage)
        return "$name:$type"
    }

    private fun generateType(ktype: KType, owningTypePackage: PackageData): String {
        val kclass = ktype.classifier
        var qualifiedName = "unknown!"
        var signature = when (kclass) {
            is KClass<*> -> {
                when {
                    kclass.java.isArray -> {
                        val t = generateType(ktype.arguments.first().type!!, owningTypePackage)
                        "$t[]"
                    }
                    else -> {
                        qualifiedName = kclass.qualifiedName!!
                        val pkgName = qualifiedName.substringBeforeLast('.')
                        val moduleVar = classModuleMap[qualifiedName]?.replace("-", "_") ?: "unknown!"
                        val name = when {
                            pkgName == owningTypePackage.qualifiedName -> kclass.simpleName!!
                            this.classModuleMap[qualifiedName] == owningTypePackage.moduleName -> qualifiedName
                            else -> "$moduleVar.$qualifiedName"
                        }
                        val typeArgs = if (kclass.typeParameters.isEmpty()) {
                            ""
                        } else {
                            val args = ktype.arguments.map {
                                if (null == it.type) { //if * projection
                                    "any"
                                } else {
                                    generateType(it.type!!, owningTypePackage)
                                }
                            }.joinToString(",")
                            "<$args>"
                        }
                        "$name$typeArgs"
                    }
                }

            }
            is KTypeParameter -> {
                kclass.name
            }
            else -> throw RuntimeException("cannot handle property type ${kclass!!::class} ${ktype}")
        }

        val mappedName = this.typeMapping.get()[qualifiedName]
        return if (null == mappedName) {
            if (signature.contains("unknown!")) {
                "any /* $signature */"
            } else {
                signature
            }
        } else {
            LOGGER.debug("Mapping " + signature + " to " + mappedName)
            mappedName
        }
    }

    private fun generateEnum(kclass: KClass<*>): String {
        val name = kclass.simpleName!!
        val literals = kclass.java.enumConstants.map {
            (it as Enum<*>).name
        }.joinToString(", ").prependIndent(INDENT)
        return "enum $name {\n$literals\n}"
    }

    /*
        private fun createModel2(classPatterns: List<String>): Map<String, Any> {
            LOGGER.info("Scanning classpath")
            LOGGER.info("Including:")
            classPatterns.forEach {
                LOGGER.info("  ${it}")
            }

            val modules = fetchModules()
            modules.forEach { moduleName, ktModule ->
                println(moduleName)
                ktModule.classes.forEach { cls ->
                    println("  $cls")
                }
            }

            val model = mutableMapOf<String, Any>()

            return model
        }

        private fun fetchModules(): Map<String, KtMetaModule> {
            val urls = mutableListOf<URL>()
            val moduleNames = mutableListOf<String>()
            val localBuild = project.buildDir.resolve("classes/kotlin/metadata/main")
            val localUrl = localBuild.absoluteFile.toURI().toURL() //URL("file://"+localBuild.absoluteFile+"/")
            urls.add(localUrl)
            moduleNames.add(project.name)

            val configurationName = "metadataDefault"
            val c = this.project.configurations.findByName(configurationName) ?: throw RuntimeException("Cannot find $configurationName configuration")
            c.isCanBeResolved = true
            for (file in c.resolve()) {
                try {
                    LOGGER.debug("Adding url for $file")
                    urls.add(file.toURI().toURL())
                    moduleNames.add(project.name)
                } catch (e: MalformedURLException) {
                    LOGGER.error("Unable to create url for $file", e)
                }

            }
            moduleNames.addAll(c.allDependencies.map { it.name })
            val cl = URLClassLoader(urls.toTypedArray(), Thread.currentThread().contextClassLoader)

            val modules = moduleNames.associate { moduleName ->
                val moduleFileBytes = cl.getResourceAsStream("META-INF/$moduleName.kotlin_module").readAllBytes()
                val module = fetchModule(moduleName, moduleFileBytes)
                classPatterns.get().forEach {
                    val pathPat = it.replace(".", "/")
                    LOGGER.info("Finding class metadata in $pathPat")
                    cl.getResourceAsStream("$pathPat").use { dirStrm ->
                        val files = getResourceFiles(dirStrm)
                        files.forEach {fileName ->
                           val clsMetaBytes =  cl.getResourceAsStream("$pathPat/fileName").readAllBytes()
                        }

                    }
                }
                Pair(moduleName, module)
            }
            return modules
        }

        private fun fetchModule(moduleName: String, moduleFileBytes: ByteArray): KtMetaModule {
            val metadata = KotlinModuleMetadata.read(moduleFileBytes) ?: throw RuntimeException("Module metadata not found for $moduleName")
            val kmModule = metadata.toKmModule()
            return KtMetaModule(kmModule)
        }
    */
    private fun getResourceFiles(dirStrm: InputStream): List<String> {
        val filenames = BufferedReader(InputStreamReader(dirStrm)).lines().toList()
        return filenames;
    }
/*
    private fun createModel(classPatterns: List<String>): Map<String, Any> {
        LOGGER.info("Scanning classpath")
        LOGGER.info("Including:")
        classPatterns.forEach {
            LOGGER.info("  ${it}")
        }

        createClassModuleMapping() //TODO: this is not very efficient, stuff is scanned more than once, here and further on

        val clToGenerate = this.createClassLoaderForDecls()
        val clForAll = this.createClassLoaderForAll()
        val packages = mutableListOf<String>()
        val classes = mutableListOf<String>()
        classPatterns.forEach { pat ->
            if (pat.endsWith(".*")) {
                packages.add(pat.substringBeforeLast(".*"))
            } else {
                classes.add(pat)
            }
        }
        val cg = ClassGraph()//
                .ignoreParentClassLoaders()
                .overrideClassLoaders(clToGenerate)//
                .enableClassInfo()//
                //.verbose()//
                //.enableExternalClasses() //
                //.enableAnnotationInfo() //
                //.enableSystemPackages()//
                .whitelistPackages(*packages.toTypedArray())
                .whitelistClasses(*classes.toTypedArray())

        val scan = cg.scan()
        val classInfo = scan.allClasses.flatMap {
            listOf(it) + it.innerClasses
        }.toSet()

        LOGGER.info("Building Datatype model for ${classInfo.size} types")

        val comparator = Comparator { c1: ClassInfo, c2: ClassInfo ->
            when {
                c1 == c2 -> 0
                c1.getSuperclasses().contains(c2) -> 1
                c2.getSuperclasses().contains(c1) -> -1
                else -> 0
            }
        }
        val filtered = classInfo.filter {
            try {
                val kclass: KClass<*> = clForAll.loadClass(it.name).kotlin
                kclass.isAbstract //this will throw exception if kotlin reflection not possible
                when {
                    kclass.isCompanion -> false
                    else -> true

                }
            } catch (ex: Throwable) {
                LOGGER.info("Skipping Class ${it.name} because kotlin reflection not supported on it")
                false
            }
        }

        val sorted = filtered.sortedWith(comparator)
        val namespaceGroups = sorted.groupBy {
            if (it.isInnerClass) {
                it.name.substringBeforeLast("$") //TODO: handle more than one level of nesting (maybe just replace $ with . here
            } else {
                it.packageName
            }
        }

        val model = mutableMapOf<String, Any>()

        val imports = dependencies.get().map {
            val m = mutableMapOf<String, Any>()
            m["name"] = it
            m["moduleVar"] = it.substringBeforeLast("-").replace("-", "_")
            m
        }
        model["import"] = imports
        val namespaces = mutableListOf<Map<String, Any>>()
        model["namespace"] = namespaces
        namespaceGroups.forEach { (nsn, classes) ->
            println(nsn)
            val namespace = mutableMapOf<String, Any>()
            namespaces.add(namespace)
            val datatypes = mutableListOf<Map<String, Any>>()
            namespace["name"] = nsn
            //TODO: this won't work if not enough subpackages
            // fix to have proper nested namespaces
            namespace["headName"] = nsn.substringBefore(".")
            namespace["tailName"] = nsn.substringAfter(".")
            namespace["datatype"] = datatypes
            for (cls in classes) {
                println(cls.name)
                val kclass: KClass<*> = clForAll.loadClass(cls.name).kotlin
                val pkgName = kclass.qualifiedName!!.substringBeforeLast('.')
                val moduleName = this.classModuleMap[kclass.qualifiedName!!] ?: "unknown"


                LOGGER.debug("Adding Class ${cls.name}")
                val dtModel = mutableMapOf<String, Any>()
                datatypes.add(dtModel)
                dtModel["name"] = kclass.simpleName!!
                dtModel["isInterface"] = cls.isInterface
                dtModel["isAbstract"] = kclass.isAbstract && cls.isInterface.not()
                dtModel["isEnum"] = cls.isEnum
                dtModel["isObject"] = null != kclass.objectInstance

                val extends_ = ArrayList<Map<String, Any>>()
                val implements_ = ArrayList<Map<String, Any>>()
                dtModel["extends"] = if (cls.isInterface) implements_ else extends_
                dtModel["implements"] = if (cls.isInterface) emptyList<Map<String, Any>>() else implements_
                kclass.supertypes.forEach { ktype ->
                    val kclass = ktype.classifier as KClass<*>
                    if (kclass == Any::class) {
                        //donothing
                    } else {
                        val mt = this.createPropertyType2(ktype, PackageData(moduleName, pkgName))
                        if (kclass.java.isInterface) {
                            implements_.add(mt)
                        } else {
                            extends_.add(mt)
                        }
                    }
                }

                val property = mutableListOf<Map<String, Any>>()
                dtModel["property"] = property
                val method = mutableListOf<Map<String, Any>>()
                dtModel["method"] = method

                if (cls.isEnum) {
                    kclass.java.enumConstants.forEach {
                        val prop = mutableMapOf<String, Any>()
                        prop["name"] = (it as Enum<*>).name
                        property.add(prop)
                    }
                } else {
                    /*
                    kclass.staticProperties.forEach {
                        println("  static $it")
                        val prop = mutableMapOf<String, Any>()
                        prop["name"] = it.name
                        prop["isStatic"] = true
                        prop["type"] = this.createPropertyType2(it.returnType, PackageData(moduleName, pkgName))
                        property.add(prop)
                    }
                    kclass.staticFunctions.forEach {
                        val meth = mutableMapOf<String, Any>()
                        meth["name"] = it.name
                        meth["isStatic"] = true
                        meth["returnType"] = this.createPropertyType2(it.returnType, PackageData(moduleName, pkgName))
                        method.add(meth)
                    }
                     */
                    kclass.declaredMemberProperties.forEach {
                        println("  $it")
                        val prop = mutableMapOf<String, Any>()
                        prop["name"] = it.name
                        prop["isStatic"] = false
                        prop["type"] = this.createPropertyType2(it.returnType, PackageData(moduleName, pkgName))
                        property.add(prop)
                    }
                    kclass.declaredMemberFunctions.forEach {
                        val meth = mutableMapOf<String, Any>()
                        meth["name"] = it.name
                        meth["isStatic"] = false
                        meth["returnType"] = this.createPropertyType2(it.returnType, PackageData(moduleName, pkgName))
                        val parameter = mutableListOf<Map<String, Any>>()
                        meth["parameter"] = parameter
                        it.parameters.forEach {
                            val param = mutableMapOf<String, Any>()
                            param["name"] = it.name ?: ""
                            param["type"] = this.createPropertyType2(it.type, PackageData(moduleName, pkgName))
                            parameter.add(param)
                        }
                        method.add(meth)
                    }
                }
            }
        }
        return model
    }
*/
    private fun createClassLoaderForAll(): URLClassLoader {
        val urls = mutableListOf<URL>()
        val forModules = moduleOnly.get()
        val localBuild = project.buildDir.resolve("classes/kotlin/${localJvmName.get()}/main")//project.tasks.getByName("jvm8MainClasses").outputs.files
        val localUrl = localBuild.absoluteFile.toURI().toURL() //URL("file://"+localBuild.absoluteFile+"/")
        urls.add(localUrl)
        val configurationName = modulesConfigurationName.get()
        val c = this.project.configurations.findByName(configurationName) ?: throw RuntimeException("Cannot find $configurationName configuration")
        //c.isCanBeResolved = true
        c.resolvedConfiguration.resolvedArtifacts.forEach { dep ->
            val file = dep.file
            try {
                LOGGER.info("For classpath adding url for $file")
                urls.add(file.toURI().toURL())
            } catch (e: MalformedURLException) {
                LOGGER.error("Unable to create url for $file", e)
            }
        }
        return URLClassLoader(urls.toTypedArray(), Thread.currentThread().contextClassLoader)
    }

    private fun createClassLoaderForDecls(): URLClassLoader {
        val urls = mutableListOf<URL>()
        val forModules = moduleOnly.get()
        println("forModules = $forModules")
        val localBuild = project.buildDir.resolve("classes/kotlin/${localJvmName.get()}/main")//project.tasks.getByName("jvm8MainClasses").outputs.files
        val localUrl = localBuild.absoluteFile.toURI().toURL() //URL("file://"+localBuild.absoluteFile+"/")
        urls.add(localUrl)
        if (localOnly.get().not()) {
            val configurationName = modulesConfigurationName.get()
            val c = this.project.configurations.findByName(configurationName) ?: throw RuntimeException("Cannot find $configurationName configuration")
            //c.isCanBeResolved = true
            c.resolvedConfiguration.resolvedArtifacts.forEach { dep ->
                println("dep = ${dep.name}")
                if (forModules.isEmpty() || forModules.contains(dep.name)) {
                    val file = dep.file
                    try {
                        LOGGER.info("For declarations adding url for $file")
                        urls.add(file.toURI().toURL())
                    } catch (e: MalformedURLException) {
                        LOGGER.error("Unable to create url for $file", e)
                    }
                }
            }
        }
        return URLClassLoader(urls.toTypedArray(), Thread.currentThread().contextClassLoader)
    }

    private fun createClassModuleMapping() {
        //hard code kotlin built-in types because scanning kotlin-stdlib.jar does not find them
        val KOTLIN_STDLIB_MODULE = "kotlin-stdlib"
        this.classModuleMap[Collection::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[List::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Set::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Map::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        this.classModuleMap[Comparable::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        //this.classModuleMap[Pair::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE
        //this.classModuleMap[KClass::class.qualifiedName!!] = KOTLIN_STDLIB_MODULE

        val localBuild = project.buildDir.resolve("classes/kotlin/${localJvmName.get()}/main")
        val localUrl = localBuild.absoluteFile.toURI().toURL()
        val moduleName = project.name
        fetchClassesFor(localUrl).forEach {
            this.classModuleMap[it] = moduleName
        }

        val configurationName = modulesConfigurationName.get()
        val c = this.project.configurations.findByName(configurationName) ?: throw RuntimeException("Cannot find $configurationName configuration")
        c.resolvedConfiguration.resolvedArtifacts.forEach { dep ->
            val moduleName = if (dep.name == "kotlin-stdlib") dep.name else dep.name.substringBeforeLast("-")
            fetchClassesFor(dep.file.toURI().toURL()).forEach {

                this.classModuleMap[it] = moduleName
            }
        }
    }

    private fun fetchClassesFor(url: URL): List<String> {
        val cl = URLClassLoader(arrayOf(url))
        val scan = ClassGraph()//
                .ignoreParentClassLoaders()
                .overrideClassLoaders(cl)//
                .enableClassInfo()//
                .scan()
        val classInfo = scan.allClasses
        return classInfo.flatMap {
            listOf(it.name) + it.innerClasses.map {
                it.name.replace("$", ".")
            }
        }
    }

    /*
        private fun createMemberName(methodName: String): String {
            return methodName.substring(3, 4).toLowerCase() + methodName.substring(4)
        }
    */
/*
    private fun _createPropertyType(ci: ClassInfo, tArgs: List<TypeArgument>, isRef: Boolean): MutableMap<String, Any> {
        val mType = mutableMapOf<String, Any>()
        val fullName = ci.getName()
        mType["fullName"] = fullName
        mType["name"] = fullName.substring(fullName.lastIndexOf('.') + 1)
        if (ci.implementsInterface("kotlin.collections.Collection")) {
            mType["isCollection"] = true
            mType["isOrdered"] = "kotlin.collections.List" == ci.getName()

            val et = this._createPropertyType(tArgs[0].getTypeSignature(), false) // what is isRef here!
            LOGGER.info("Collection with elementType " + tArgs[0].getTypeSignature())
            mType["elementType"] = et

        } else if (ci.isEnum()) {
            mType["isEnum"] = true
        }
        mType["isReference"] = isRef
        return mType
    }

    private fun _createPropertyType(typeSig: TypeSignature, isRef: Boolean): Map<String, Any> {
        var mType: MutableMap<String, Any> = mutableMapOf()
        if (typeSig is BaseTypeSignature) {
            val bts = typeSig as BaseTypeSignature
            mType["name"] = bts.getTypeStr()
            mType["fullName"] = bts.getTypeStr()
        } else if (typeSig is ArrayTypeSignature) {
            val rts = typeSig as ArrayTypeSignature
            mType["name"] = "array"
            mType["fullName"] = "array"
            mType["isCollection"] = true
            mType["elementType"] = this._createPropertyType(rts.getElementTypeSignature(), isRef)
        } else if (typeSig is ClassRefTypeSignature) {
            val cts = typeSig as ClassRefTypeSignature
            if (null == cts.getClassInfo()) {
                val fullName = cts.getFullyQualifiedClassName()
                mType["fullName"] = fullName
                mType["name"] = fullName.substring(fullName.lastIndexOf('.') + 1)
                LOGGER.error("Unable to find class info for " + cts.getFullyQualifiedClassName())
            } else {
                mType = this._createPropertyType(cts.getClassInfo(), cts.getTypeArguments(), isRef)
            }
        } else {
        }

        val mappedName = this.typeMapping.get()[mType["fullName"]]
        if (null == mappedName) {
            return mType
        } else {
            LOGGER.debug("Mapping " + mType["name"] + " to " + mappedName)
            mType["name"] = mappedName
            return mType
        }

    }
*/
    private fun createPropertyType2(ktype: KType, owningTypePackage: PackageData): Map<String, Any> {
        var mType: MutableMap<String, Any> = mutableMapOf()
        val kclass = ktype.classifier
        var qualifiedName = "unknown"
        when (kclass) {
            is KClass<*> -> {
                qualifiedName = kclass.qualifiedName!!
                mType = createPropertyTypeFromKClass(kclass, owningTypePackage)
                if (kclass.typeParameters.isNotEmpty()) {
                    val elementTypes = ktype.arguments.map {
                        if (null == it.type) { //if * projection
                            mType["name"] = "any"
                            mType["isGeneric"] = false
                        } else {
                            this.createPropertyType2(it.type!!, owningTypePackage)
                        }
                    }
                    mType["elementType"] = elementTypes
                }
            }
            is KTypeParameter -> {
                qualifiedName = kclass.name
                mType["name"] = kclass.name
                mType["isGeneric"] = false
            }
            else -> throw RuntimeException("cannot handle property type ${kclass!!::class} ${ktype}")
        }

        val mappedName = this.typeMapping.get()[qualifiedName]
        return if (null == mappedName) {
            mType
        } else {
            LOGGER.debug("Mapping " + mType["name"] + " to " + mappedName)
            mType["name"] = mappedName
            mType
        }

    }

    private fun createPropertyTypeFromKClass(kclass: KClass<*>, owningTypePackage: PackageData): MutableMap<String, Any> {
        val moduleVar = classModuleMap[kclass.qualifiedName!!]?.replace("-", "_") ?: "unknown"
        val qualifiedName = kclass.qualifiedName!!
        var mType: MutableMap<String, Any> = mutableMapOf()
        val pkgName = qualifiedName.substringBeforeLast('.')
        mType["name"] = when {
            pkgName == owningTypePackage.qualifiedName -> kclass.simpleName!!
            this.classModuleMap[qualifiedName] == owningTypePackage.moduleName -> qualifiedName
            else -> "$moduleVar.$qualifiedName"
        }
        mType["isGeneric"] = kclass.typeParameters.isNotEmpty()
        return mType
    }
}
