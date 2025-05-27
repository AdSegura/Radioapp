plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.dagger.hilt.android") version "2.48" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

// En el archivo settings.gradle.kts o build.gradle.kts (nivel proyecto)
buildscript {

    dependencies {
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.48")
    }
}