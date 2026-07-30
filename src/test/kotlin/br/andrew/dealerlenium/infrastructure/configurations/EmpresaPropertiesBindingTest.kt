package br.andrew.dealerlenium.infrastructure.configurations

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.env.StandardEnvironment
import kotlin.test.assertEquals

class EmpresaPropertiesBindingTest {
    @Test
    fun `preserves empresa map key with space only when bracketed`() {
        assertEquals(
            setOf("AUTOVEMA_PVH", "AUTOVEMA_ECAR_PVH"),
            bindKeys(
                """
                empresa:
                  configuracoes:
                    AUTOVEMA_PVH:
                      cnpj: "03968287000136"
                      marca: FIAT
                      adiantamento:
                        conta-gerencial-identificador: 30212001
                        tipo-codigo: 5
                        tipo-documento-codigo: 170
                        departamento: ADMINISTRAÇÃO
                        tipo-ficha-razao: ADIANT DE CLIENTES (PEÇAS)
                    "AUTOVEMA_ECAR_ PVH":
                      cnpj: "14922890000101"
                      marca: "OMODA JAECOO"
                      adiantamento:
                        conta-gerencial-identificador: 30209001
                        tipo-codigo: 5
                        tipo-documento-codigo: 170
                        departamento: ADMINISTRAÇÃO
                        tipo-ficha-razao: ADIANT DE CLIENTES (PEÇAS)
                """.trimIndent(),
            ),
        )

        assertEquals(
            setOf("AUTOVEMA_PVH", "AUTOVEMA_ECAR_ PVH"),
            bindKeys(
                """
                empresa:
                  configuracoes:
                    AUTOVEMA_PVH:
                      cnpj: "03968287000136"
                      marca: FIAT
                      adiantamento:
                        conta-gerencial-identificador: 30212001
                        tipo-codigo: 5
                        tipo-documento-codigo: 170
                        departamento: ADMINISTRAÇÃO
                        tipo-ficha-razao: ADIANT DE CLIENTES (PEÇAS)
                    "[AUTOVEMA_ECAR_ PVH]":
                      cnpj: "14922890000101"
                      marca: "OMODA JAECOO"
                      adiantamento:
                        conta-gerencial-identificador: 30209001
                        tipo-codigo: 5
                        tipo-documento-codigo: 170
                        departamento: ADMINISTRAÇÃO
                        tipo-ficha-razao: ADIANT DE CLIENTES (PEÇAS)
                """.trimIndent(),
            ),
        )
    }

    private fun bindKeys(yaml: String): Set<String> {
        val environment = StandardEnvironment()
        YamlPropertySourceLoader()
            .load("empresa-test", ByteArrayResource(yaml.toByteArray()))
            .asReversed()
            .forEach { environment.propertySources.addFirst(it) }

        return Binder.get(environment)
            .bind("empresa", EmpresaProperties::class.java)
            .get()
            .configuracoes
            .keys
    }
}
