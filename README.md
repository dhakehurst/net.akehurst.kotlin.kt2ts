# net.akehurst.kotlin.kt2ts
Typescript definition file generator for kotlin (js) classes

Kotlin is great. However, if you want to integrate it into an angular application (or other typescript) we need to generate the typescript declaration file and package.json. Neither of these is currently created by the kotlin JS compiler.

This plugin is intended for use in a kotlin multiplatform development. It uses JVM reflection over the kotlin-JVM module to generate the .d.ts file that is intended for use with the kotlin-js module.

The plugin addresses two use cases:
1. generating .d.ts file for your own kotlin module
2. generating .d.ts file for a third-party module


## Add the plugin

```
plugins {
    id("net.akehurst.kotlin.kt2ts") version "1.0.0"
}
```

## configure the plugin for your own modules

```
// store this value, you need it in more than one place
val tsdDir ="${buildDir}/tmp/jsJar/ts"

// add the generated .d.ts file to the module resources
kotlin {
    sourceSets {
        val jsMain by getting {
            resources.srcDir("${tsdDir}")
        }
    }
}

// configure the kt2ts plugin
kt2ts {
    // name of the jvm configuration for this module (locally build classes) [default 'jvm']
    localJvmName.set("jvm8")
    
    // name of the configuration to use for finding depended modules [default 'jvmRuntimeClasspath']
    modulesConfigurationName.set("jvm8RuntimeClasspath")
    
    // directory to output generated files into
    outputDirectory.set(file("${tsdDir}"))
    
    // list of qualified class names to generate declarations for
    // '*' means all classes in the package
    classPatterns.set(listOf(
            "net.akehurst.kotlin.example.addressbook.information.*"
    ))
}
tasks.getByName("generateTypescriptDefinitionFile").dependsOn("jvm8MainClasses")
tasks.getByName("jsJar").dependsOn("generateTypescriptDefinitionFile")
```

## configure the plugin for thrid party modules
  TODO
