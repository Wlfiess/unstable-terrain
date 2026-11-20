plugins { java; id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" }
group = "dev.greatsea"
version = "1.0"
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
val embedded by configurations.creating
dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly(files("libs/terrain-core.jar"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation(files("libs/terrain-core.jar"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    embedded("org.ow2.asm:asm-tree:9.9.1")
    implementation("org.ow2.asm:asm-tree:9.9.1")
}
java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
tasks.withType<JavaCompile>().configureEach { options.release = 21 }
tasks.test { useJUnitPlatform() }
tasks.jar {
    manifest.attributes["paperweight-mappings-namespace"] = "mojang"
    from(zipTree("libs/terrain-core.jar"))
    from(embedded.map { zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
