package br.andrew.dealerlenium

import org.mockito.ArgumentMatchers

/**
 * Matchers do Mockito com tipo generico solto: o retorno de `ArgumentMatchers.any()`
 * e nulo, e Kotlin rejeita passar isso para um parametro nao-nulo.
 */
fun <T> anyArg(): T = ArgumentMatchers.any()
