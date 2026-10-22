plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "com.kigtts.input.gestures"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
    testOptions.unitTests.isIncludeAndroidResources = true
}
tasks.register<Sync>("collectPluginDependencies") { from(configurations.getByName("debugRuntimeClasspath")); into(layout.buildDirectory.dir("plugin-dependencies")) }
dependencies {
    compileOnly(project(":plugin-android-api"))
    testImplementation(project(":plugin-api"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16")
}
