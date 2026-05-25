package br.andrew.dealerlenium.infrastructure

import br.andrew.dealerlenium.model.TransactionStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

@Configuration
class MongoConfig {
    @Bean
    fun mongoCustomConversions(): MongoCustomConversions {
        return MongoCustomConversions(
            listOf(
                StringToTransactionStatusConverter(),
                TransactionStatusToStringConverter(),
            ),
        )
    }
}

@ReadingConverter
class StringToTransactionStatusConverter : Converter<String, TransactionStatus> {
    override fun convert(source: String): TransactionStatus = TransactionStatus.from(source)
}

@WritingConverter
class TransactionStatusToStringConverter : Converter<TransactionStatus, String> {
    override fun convert(source: TransactionStatus): String = source.value
}
