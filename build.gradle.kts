plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "br.andrew"
version = findProperty("projectVersion")?.toString() ?: "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// O BOM do Spring Boot 3.5 fixa Selenium 4.31.0; alinha com a versao exigida pelo Selenide.
extra["selenium.version"] = "4.51.0"

sourceSets {
    named("test") {
        resources.srcDir("environments")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.codeborne:selenide:7.19.1")
    implementation("net.sourceforge.tess4j:tess4j:5.20.0")
    testRuntimeOnly("org.seleniumhq.selenium:selenium-devtools-v155:4.51.0") {
        isTransitive = false
    }
    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    val dealerTestContext = providers.gradleProperty("dealerTestContext")
        .orElse(providers.systemProperty("dealer.test.context"))
        .orElse(providers.systemProperty("DEALER_TEST_CONTEXT"))
        .orElse(providers.environmentVariable("DEALER_TEST_CONTEXT"))
        .getOrElse("hmg")

    systemProperty("DEALER_TEST_CONTEXT", dealerTestContext)
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
    jvmToolchain(25)
}
