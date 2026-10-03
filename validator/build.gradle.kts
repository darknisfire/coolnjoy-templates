import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
}

group = "net.coolnjoy.templates"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

// 앱 core의 템플릿 엔진 스냅샷(ENGINE_SNAPSHOT.md 참고). scripts/sync-engine.mjs로 갱신한다.
sourceSets.main {
    kotlin.srcDir("engine-src")
}

dependencies {
    // 버전은 앱 저장소 core/build.gradle.kts와 같게 유지한다.
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val repoRoot = layout.projectDirectory.dir("..").asFile

fun Test.commonConfig() {
    systemProperty("repo.root", repoRoot.absolutePath)
    systemProperty("file.encoding", "UTF-8")
    inputs.dir(File(repoRoot, "fixtures")).withPropertyName("fixtures")
    inputs.file(File(repoRoot, "templates/site.json")).withPropertyName("template")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.test {
    commonConfig()
    // -PupdateSnapshots: fixtures/expected/ 를 엔진 출력으로 다시 쓴다.
    if (project.hasProperty("updateSnapshots")) {
        systemProperty("updateSnapshots", "true")
        outputs.upToDateWhen { false }
    }
    useJUnitPlatform {
        excludeTags("live")
    }
}

tasks.register<Test>("liveTest") {
    description = "Runs tests tagged 'live' (GET against the real site)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    commonConfig()
    outputs.upToDateWhen { false }
    useJUnitPlatform {
        includeTags("live")
    }
}
