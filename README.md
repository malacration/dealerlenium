# Dealerlenium

Build e execução usam Java 25 LTS, Kotlin 2.4.21 e Gradle 9.8.2 pelo wrapper. Selenide 7.19.1 e Selenium 4.51.0 estão alinhados, inclusive o módulo DevTools.

```sh
./gradlew bootJar --no-daemon
docker build .
```

Spring Boot permanece em 3.5.16 para preservar Jackson 2, a configuração de segurança e MongoDB. A linha 3.5 encerrou o suporte comunitário; migrar para Boot 4 requer homologação dos fluxos reais de automação financeira.

Na atualização, 104 testes passaram usando um init script externo para excluir as tags `dealer-pessoa-lookup` e `dealer-adiantamento-pipeline`. Esses cenários acessam o Dealer real e não foram executados. O comando `./gradlew test` inclui os testes de integração existentes; o ambiente e as credenciais determinam quais podem executar. O estágio Docker `builder` também foi compilado.

Para repetir apenas a validação local, crie um init script Groovy fora do repositório:

```groovy
// /tmp/dealer-tests-locais.gradle
allprojects {
    tasks.withType(Test).configureEach {
        useJUnitPlatform {
            excludeTags 'dealer-pessoa-lookup', 'dealer-adiantamento-pipeline'
        }
    }
}
```

Execute `./gradlew -I /tmp/dealer-tests-locais.gradle test bootJar --no-daemon --max-workers=1` com Java 25.
