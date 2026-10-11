import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // GitHub Actions içinde otomatik "kullanici/depo" olur.
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "kullanici/depo")
        authors = listOf("Darknes Lord")
    }

    android {
        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        tasks.withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = "1.8"
                freeCompilerArgs = freeCompilerArgs +
                    "-Xno-call-assertions" +
                    "-Xno-param-assertions" +
                    "-Xno-receiver-assertions"
            }
        }
    }

    dependencies {
        // CloudStream API taslakları: sürüme göre yapılandırma adı "cloudstream" ya da "apk"
        val stubs = configurations.findByName("cloudstream")?.name
            ?: configurations.findByName("apk")?.name
            ?: error("CloudStream gradle eklentisi yapılandırması bulunamadı")
        add(stubs, "com.lagradost:cloudstream3:pre-release")

        add("implementation", kotlin("stdlib"))
        // Yalnızca derleme için; çalışma anında bunlar CloudStream uygulamasından gelir.
        add("implementation", "androidx.appcompat:appcompat:1.7.0")
        add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
