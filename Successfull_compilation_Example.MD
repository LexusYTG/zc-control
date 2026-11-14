ESTE ES UN SEGUIMIENTO AL PIE DE LA LETRA DE LA GUIA DE CONPILACION, ESTO ES LO QUE DEBERIAS VER SI TIENES TODOS LOS REQUISITOS CUMPLIDOS Y SIGUES LA GUIA A RAJATABLA


~ $ ls                                                                          SHELL-AND  android-sdk  storage
~ $ git clone https://github.com/LexusYTG/zc-control.git                        Cloning into 'zc-control'...
remote: Enumerating objects: 885, done.                                         remote: Counting objects: 100% (885/885), done.
remote: Compressing objects: 100% (432/432), done.                              remote: Total 885 (delta 268), reused 860 (delta 243), pack-reused 0 (from 0)
Receiving objects: 100% (885/885), 4.40 MiB | 993.00 KiB/s, done.               Resolving deltas: 100% (268/268), done.
~ $ ls                                                                          SHELL-AND  android-sdk  storage  zc-control
~ $ cd zc-control                                                               ~/zc-control $ ls
LICENSE    app    build.gradle.kts   settings.gradle.kts                        README.md  build  gradle.properties
~/zc-control $ gradle assembleDebug                                             Warning: Unable to set daemon's environment variables to match the client because:
  There is no native integration with this operating environment.                 If the daemon was started with a significantly different environment from the client, and your build
  relies on environment variables, you may experience unexpected behavior.      
> Configure project :app                                                        WARNING: The option setting 'android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2' is experimental.                                         
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:checkKotlinGradlePluginConfigurationErrors
> Task :app:generateDebugResValues
> Task :app:checkDebugAarMetadata
> Task :app:mapDebugSourceSetPaths
> Task :app:generateDebugResources
> Task :app:mergeDebugResources
> Task :app:packageDebugResources
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:parseDebugLocalResources
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:javaPreCompileDebug
> Task :app:mergeDebugShaders
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets
> Task :app:compressDebugAssets
> Task :app:desugarDebugFileDependencies
> Task :app:mergeDebugStartupProfile UP-TO-DATE
> Task :app:checkDebugDuplicateClasses
> Task :app:mergeDebugJniLibFolders
> Task :app:processDebugManifestForPackage
> Task :app:mergeLibDexDebug
> Task :app:mergeDebugNativeLibs NO-SOURCE
> Task :app:stripDebugDebugSymbols NO-SOURCE
> Task :app:validateSigningDebug
> Task :app:writeDebugAppMetadata
> Task :app:writeDebugSigningConfigVersions
> Task :app:processDebugResources
> Task :app:mergeExtDexDebug
> Task :app:compileDebugKotlin
w: file:///data/data/com.termux/files/home/zc-control/app/src/main/java/com/lexus2026/cdc/PanelView.kt:169:38 'getDefaultAdapter(): BluetoothAdapter!' is deprecated. Deprecated in Java
w: file:///data/data/com.termux/files/home/zc-control/app/src/main/java/com/lexus2026/cdc/PanelView.kt:171:45 'getDefaultAdapter(): BluetoothAdapter!' is deprecated. Deprecated in Java

> Task :app:compileDebugJavaWithJavac NO-SOURCE
> Task :app:dexBuilderDebug
> Task :app:mergeDebugGlobalSynthetics
> Task :app:processDebugJavaRes
> Task :app:mergeProjectDexDebug
> Task :app:mergeDebugJavaResource
> Task :app:packageDebug
> Task :app:createDebugApkListingFileRedirect
> Task :app:assembleDebug

[Incubating] Problems report is available at: file:///data/data/com.termux/files/home/zc-control/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.9.1/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 1m 33s
35 actionable tasks: 34 executed, 1 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.9.1/userguide/configuration_cache_enabling.html
~/zc-control $ adb install app/build/outputs/apk/debug/app-debug.apk
Performing Streamed Install
Success
~/zc-control $
