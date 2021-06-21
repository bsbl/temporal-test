package seb.temporal.test.utils

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.UUID
import java.util.concurrent.Future

/**
 * Kafka extension to send event to a Kafka topic
 * Manages to convert the key as a string (toString())
 * and the payload as a json (Jackson ObjectMapper::writeValueAsString)
 * @param topic the topic to publish the event to
 * @param k the key to used to publish into Kafka. Null allowed.
 * @param v the payload to publish into Kafka.
 */
fun <K,V> KafkaProducer<String, String>.send(topic: String, k: K?, v: V): Future<RecordMetadata> {
    val key: String? = when(k) {
        null -> null
        is String -> k
        is UUID -> k.toString()
        else -> k.toString()
    }
    val value: String = when(v) {
        is String -> v
        else -> Json.objectMapper.writeValueAsString(v)
    }
    return send(ProducerRecord(topic, key, value))
}
