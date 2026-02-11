plugins {
    kotlin("jvm") version "1.9.22"
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}
