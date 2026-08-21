package br.andrew.dealerlenium.schedule

import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Pausa entre chamadas do monitoramento. Existe como interface para os testes
 * verificarem o espacamento sem gastar tempo real.
 */
fun interface Sleeper {
    @Throws(InterruptedException::class)
    fun sleep(duration: Duration)
}

@Component
class ThreadSleeper : Sleeper {
    override fun sleep(duration: Duration) {
        if (duration.isNegative || duration.isZero) {
            return
        }
        Thread.sleep(duration.toMillis())
    }
}
