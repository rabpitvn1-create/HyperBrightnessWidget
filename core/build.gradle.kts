plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1") }
tasks.register<JavaExec>("relayCheck") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("relay.core.RelayTestKt")
}
