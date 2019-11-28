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
    id("net.akehurst.kotlin.kt2ts") version "1.1.0"
}
```

## configure the plugin for your own modules

```
// configure the kt2ts plugin
kt2ts {
    // name of the jvm configuration for this module (locally build classes) [default 'jvm']
    localJvmName.set("jvm8")
    
    // list of qualified class names to generate declarations for
    // '*' means all classes in the package
    classPatterns.set(listOf(
            "net.akehurst.kotlin.example.addressbook.information.*"
    ))
}
```

## configure the plugin for third-party modules
  doc TODO
