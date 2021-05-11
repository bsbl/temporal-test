package seb.temporal.test.inbound

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import seb.temporal.test.glue.KafkaConfiguration
import seb.temporal.test.glue.KafkaResourceFactory
import seb.temporal.test.utils.Json
import seb.temporal.test.utils.logger
import java.math.BigDecimal

@RestController
@RequestMapping("order")
class RestController(@Autowired private val kafkaResourceFactory: KafkaResourceFactory) {

    private val log by logger()

    // force non null / required as per configuration
    private val producer: KafkaProducer<String, String> = kafkaResourceFactory.newProducer(KafkaConfiguration.inboundTopic)!!

    @PostMapping("v1/new")
    fun new(@RequestBody order: OrderReq): String {
        val key = order.id
        log.info("Publish message with orderId $key")
        val future = producer.send(ProducerRecord(
                KafkaConfiguration.inboundTopic, key, Json.objectMapper.writeValueAsString(order)))
        log.info("Waiting (until retries finish) for ack(s) with orderId $key")
        val record = future.get()
        log.info("Message published successfully for orderId $key, record=$record")
        return key;
    }

}

data class OrderReq @JsonCreator constructor(
        @JsonProperty("id") val id: String,
        @JsonProperty("qty") val qty: BigDecimal,
        @JsonProperty("product") val product: ProductReq
)

data class ProductReq @JsonCreator constructor(
        @JsonProperty("assetClass") val assetClass: String,
        @JsonProperty("isin") val isin: String
)