plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "br.andrew"
version = findProperty("projectVersion")?.toString() ?: "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.codeborne:selenide:7.12.0")
    implementation("net.sourceforge.tess4j:tess4j:5.18.0")
    testRuntimeOnly("org.seleniumhq.selenium:selenium-devtools-v148:4.44.0") {
        isTransitive = false
    }
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Test>("dealerAdiantamentoPipelineTest") {
    description = "Executa o teste de pipeline da baixa de adiantamento usando o Dealer real."
    group = "verification"
    useJUnitPlatform {
        includeTags("dealer-adiantamento-pipeline")
    }
    shouldRunAfter(tasks.test)
}

tasks.register<Test>("dealerPessoaLookupIntegrationTest") {
    description = "Executa o teste de integracao de busca de pessoa no Dealer real."
    group = "verification"
    useJUnitPlatform {
        includeTags("dealer-pessoa-lookup")
    }
    shouldRunAfter(tasks.test)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
kotlin {
    jvmToolchain(21)
}
