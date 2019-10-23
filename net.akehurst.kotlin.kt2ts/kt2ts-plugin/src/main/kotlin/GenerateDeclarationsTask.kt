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

import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import com.github.jknack.handlebars.io.FileTemplateLoader
import io.github.classgraph.*
import kotlinx.metadata.KmClass
import kotlinx.metadata.jvm.KmModule
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.KotlinModuleMetadata
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.slf4j.LoggerFactory
import java.io.*
import java.lang.RuntimeException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import kotlin.Comparator
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses
import kotlin.streams.toList

data class PackageData(
        val moduleName:String,
        val qualifiedName:String
)

class KtMetaModule(
        val metadata: KmModule
) {
    val classes = mutableListOf<KmClass>()
}

open class GenerateDeclarationsTask : DefaultTask() {

    companion object {
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

    @get:Input
    var templateFileName = project.objects.property(String::class.java)

    @get:InputDirectory
    @get:Optional
    var templateDir = project.objects.directoryProperty()

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
        //this.templateDir.set(null as Directory?)
        this.templateFileName.set("template.hbs")
        this.typeMapping.set(mapOf(
                "kotlin.Any" to "any",
                "kotlin.String" to "string",
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

    @TaskAction
    internal fun exec() {
        LOGGER.info("Executing $NAME")

        val model = this.createModel(this.classPatterns.get())
        //val model2 = this.createModel2(this.classPatterns.get())
        val td = this.templateDir.orNull?.asFile
        val of = this.declarationsFile.get().asFile
        this.generate(model, td, of)

    }

    private fun generate(model: Map<String, Any>, templateDir: File?, outputFile: File) {
        LOGGER.info("Generating ", outputFile)
        // final DefaultMustacheFactory mf = new DefaultMustacheFactory();
        if (outputFile.exists() && overwrite.get().not()) {
            LOGGER.info("Skipping $outputFile as it exists, set overwrite=true to overwrite the file")
        } else {
            if (outputFile.exists()) {
                LOGGER.info("Overwriting existing $outputFile, set overwrite=false to keep the orginal")
            }
            val ftl = when {
                null != templateDir -> {
                    LOGGER.info("Using template directory $templateDir")
                    FileTemplateLoader(templateDir, "")
                }
                else -> {
                    LOGGER.info("Using default template")
                    ClassPathTemplateLoader("/handlebars")
                }
            }
            val hb = Handlebars(ftl)

            try {
                LOGGER.info("Compiling template ${templateFileName.get()}")
                val tn = templateFileName.get()
                val tmn = if (tn.endsWith(".hbs")) tn.removeSuffix(".hbs") else tn
                val t = hb.compile(tmn)

                LOGGER.info("Generating output")
                val writer = FileWriter(outputFile)

                val ctx = Context.newBuilder(model).resolver(MapValueResolver.INSTANCE).build()

                t.apply(ctx, writer)
                writer.close()
            } catch (e: Exception) {
                LOGGER.error("Unable to generate", e)
            }
        }
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


    private fun createModel(classPatterns: List<String>): Map<String, Any> {
        LOGGER.info("Scanning classpath")
        LOGGER.info("Including:")
        classPatterns.forEach {
            LOGGER.info("  ${it}")
        }

        createClassModuleMapping() //TODO: this is not very efficient, stuff is scanned more than once, here and further on

        val clToGenerate = this.createClassLoaderForDecls()
        val clForAll = this.createClassLoaderForAll()
        val scan = ClassGraph()//
                .ignoreParentClassLoaders()
                .overrideClassLoaders(clToGenerate)//
                .enableClassInfo()//
                //.verbose()//
                //.enableExternalClasses() //
                //.enableAnnotationInfo() //
                //.enableSystemPackages()//
                .whitelistPackages(*classPatterns.toTypedArray())//
                .scan()
        val classInfo = scan.allClasses

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
            it.name.contains("$").not()
        }

        val sorted = filtered.sortedWith(comparator)
        val namespaceGroups = sorted.groupBy { it.packageName }

        val model = mutableMapOf<String, Any>()

        val imports = dependencies.get().map {
            val m = mutableMapOf<String, Any>()
            m["name"] = it
            m["moduleVar"] = it.substringBeforeLast("-").replace("-","_")
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

                val kclass: KClass<*> = clForAll.loadClass(cls.name).kotlin
                val pkgName = kclass.qualifiedName!!.substringBeforeLast('.')
                val moduleName = this.classModuleMap[kclass.qualifiedName!!] ?: "unknown"

                var reflectable = true
                try {
                    //this will throw exception if kotlin reflection not possible
                    kclass.isAbstract
                } catch (ex:Throwable) {
                    reflectable = false
                }

                if (reflectable) {
                    println(cls.name)
                    LOGGER.debug("Adding Class ${cls.name}")
                    val dtModel = mutableMapOf<String, Any>()
                    datatypes.add(dtModel)
                    dtModel["name"] = kclass.simpleName!!
                    dtModel["moduleVar"] = moduleName.replace("-", "_")
                    dtModel["qualifiedName"] = kclass.qualifiedName!!
                    dtModel["fullyQualifiedName"] = "${dtModel["moduleVar"]}.${dtModel["qualifiedName"]}"
                    dtModel["isInterface"] = cls.isInterface
                    dtModel["isAbstract"] = kclass.isAbstract
                    dtModel["isEnum"] = cls.isEnum

                    val extends_ = ArrayList<Map<String, Any>>()
                    val implements_ = ArrayList<Map<String, Any>>()
                    dtModel["extends"] = extends_
                    dtModel["implements"] = implements_
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
                    kclass.memberProperties.forEach {
                        val prop = mutableMapOf<String, Any>()
                        prop["name"] = it.name
                        prop["type"] = this.createPropertyType2(it.returnType, PackageData(moduleName, pkgName))
                        property.add(prop)
                    }
                } else {
                    LOGGER.info("Skipping Class ${cls.name} because kotlin reflection not supported on it")
                }
            }
        }
        return model
    }

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
        val localBuild = project.buildDir.resolve("classes/kotlin/${localJvmName.get()}/main")
        val localUrl = localBuild.absoluteFile.toURI().toURL()
        fetchClassesFor(localUrl).forEach {
            val moduleName = project.name
            try {
                this.classModuleMap[it.qualifiedName!!] = moduleName
            } catch(t:Throwable) {
                try {
                    LOGGER.debug("Ignored class ${it}, cannot get qualifiedName")
                } catch(t:Throwable) {
                    LOGGER.debug("Ignored class, unknown error")
                }
            }
        }

        val configurationName = modulesConfigurationName.get()
        val c = this.project.configurations.findByName(configurationName) ?: throw RuntimeException("Cannot find $configurationName configuration")
        c.resolvedConfiguration.resolvedArtifacts.forEach { dep ->
            fetchClassesFor(dep.file.toURI().toURL()).forEach {
                val moduleName = dep.name.substringBeforeLast("-")
                try {
                    this.classModuleMap[it.qualifiedName!!] = moduleName
                } catch(t:Throwable) {
                    try {
                        LOGGER.debug("Ignored class ${it}, cannot get qualifiedName")
                    } catch(t:Throwable) {
                        LOGGER.debug("Ignored class, unknown error")
                    }
                }
            }
        }
    }

    private fun fetchClassesFor(url:URL) : List<KClass<*>> {
        val cl = URLClassLoader(arrayOf(url))
        val scan = ClassGraph()//
                .ignoreParentClassLoaders()
                .overrideClassLoaders(cl)//
                .enableClassInfo()//
                .scan()
        val classInfo = scan.allClasses
        return classInfo.mapNotNull {
            try {
                it.loadClass().kotlin
            } catch (t:Throwable) {
                LOGGER.debug("Ignored class ${it.name}, cannot load class")
                null
            }
        }
    }

/*
    private fun createMemberName(methodName: String): String {
        return methodName.substring(3, 4).toLowerCase() + methodName.substring(4)
    }
*/

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

    private fun createPropertyType2(kclass: KClass<*>, owningTypePackage: PackageData): Map<String, Any> {
        var mType: MutableMap<String, Any> = mutableMapOf()
        val pkgName = kclass.qualifiedName!!.substringBeforeLast('.')
        mType["name"] = kclass.simpleName!!
        mType["qualifiedName"] = kclass.qualifiedName!!
        mType["moduleVar"] = classModuleMap[kclass.qualifiedName!!]?.replace("-", "_") ?: "unknown"
        mType["fullyQualifiedName"] = "${mType["moduleVar"]}.${mType["qualifiedName"]}"
        mType["isCollection"] = kclass.isSubclassOf(Collection::class)
        mType["isSamePackage"] = pkgName == owningTypePackage.qualifiedName
        mType["isSameModule"] = this.classModuleMap[kclass.qualifiedName!!] == owningTypePackage.moduleName

        val mappedName = this.typeMapping.get()[mType["qualifiedName"]]
        return if (null == mappedName) {
            mType
        } else {
            LOGGER.debug("Mapping " + mType["name"] + " to " + mappedName)
            mType["name"] = mappedName
            mType["qualifiedName"] = mappedName
            mType["fullyQualifiedName"] = mappedName
            mType
        }
    }

    private fun createPropertyType2(ktype: KType, owningTypePackage: PackageData): Map<String, Any> {
        var mType: MutableMap<String, Any> = mutableMapOf()
        val kclass = ktype.classifier

        when (kclass) {
            is KClass<*> -> {
                val pkgName = kclass.qualifiedName!!.substringBeforeLast('.')
                mType["name"] = kclass.simpleName!!
                mType["qualifiedName"] = kclass.qualifiedName!!
                mType["moduleVar"] = classModuleMap[kclass.qualifiedName!!]?.replace("-", "_") ?: "unknown"
                mType["fullyQualifiedName"] = "${mType["moduleVar"]}.${mType["qualifiedName"]}"
                mType["isCollection"] = kclass.isSubclassOf(Collection::class)
                mType["isSamePackage"] = pkgName == owningTypePackage.qualifiedName
                mType["isSameModule"] = this.classModuleMap[kclass.qualifiedName!!] == owningTypePackage.moduleName

                if (kclass.isSubclassOf(Collection::class)) {
                    val elementTypes = ktype.arguments.map {
                        this.createPropertyType2(it.type!!, owningTypePackage)
                    }
                    mType["elementType"] = elementTypes
                }
            }
            is KTypeParameter -> {
                mType["name"] = kclass.name
                mType["qualifiedName"] = kclass.name
                mType["isSamePackage"] = true
                mType["isSameModule"] = true
                mType["isCollection"] = false
            }
            else -> throw RuntimeException("cannot handle property type ${kclass!!::class} ${ktype}")
        }

        val mappedName = this.typeMapping.get()[mType["qualifiedName"]]
        return if (null == mappedName) {
            mType
        } else {
            LOGGER.debug("Mapping " + mType["name"] + " to " + mappedName)
            mType["name"] = mappedName
            mType["qualifiedName"] = mappedName
            mType["fullyQualifiedName"] = mappedName
            mType
        }

    }

}
