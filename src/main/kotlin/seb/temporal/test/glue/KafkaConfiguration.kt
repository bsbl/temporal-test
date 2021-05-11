package seb.temporal.test.glue

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import seb.temporal.test.utils.logger
import java.util.Properties
import java.util.regex.Pattern


// KStream is interesting for the exactly once semantic
// KStream: https://github.com/gwenshap/kafka-streams-wordcount/
// groupByKey() !!
// https://stackoverflow.com/questions/51299528/handling-exceptions-in-kafka-streams
// https://www.confluent.fr/blog/introducing-kafka-streams-stream-processing-made-simple/
// https://www.confluent.io/blog/kafka-streams-tables-part-3-event-processing-fundamentals/
// https://docs.confluent.io/platform/current/streams/faq.html
// consumer shutdown hook
// https://github.com/gwenshap/kafka-examples/blob/master/SimpleMovingAvg/src/main/java/com/shapira/examples/newconsumer/simplemovingavg/SimpleMovingAvgNewConsumer.java
// dead letter queue:
// https://docs.confluent.io/platform/current/streams/faq.html
// Kafka stream config ref:
// https://kafka.apache.org/10/documentation/streams/developer-guide/config-streams.html
@Configuration
@EnableConfigurationProperties(KafkaConfig::class)
class KafkaConfiguration {

    @Bean(initMethod = "start")
    fun kafkaResourceFactory(kafkaConfig: KafkaConfig): KafkaResourceFactory = KafkaResourceFactory(kafkaConfig)

    //TODO to be externalised in yaml
    companion object {
        /**
         * Events published by the inbound gateway
         */
        val inboundTopic = "seb-test-inbound-gw"

        /**
         * Event published by the order workflow
         */
        val orderTopic = "seb-test-order"

        /**
         * Event published to the execution gateway
         */
        val executionGwTopic = "seb-test-exec-gw"

        /**
         * Event published by the execution workflow
         */
        val executionTopic= "seb-test-exec"
    }
}

@ConstructorBinding
@ConfigurationProperties(prefix = "kafka")
data class KafkaConfig(val bootstrapServers: String, val common: Properties?,
                       val producerDefault: Properties?, val consumerDefault: Properties?,
                       val topics: Map<String, KafkaTopicConfig>?,
                       val consumers: Map<String, KafkaTopicConsumerConfig>?,
                       val streams: Map<String, KafkaStreamConfig>?)

@ConstructorBinding
data class KafkaTopicConfig(val name: String, val admin: KafkaTopicAdminConfig?, val producer: Properties?)

@ConstructorBinding
data class KafkaTopicAdminConfig(val nbPartitions: Int, val nbReplicas: Short)

@ConstructorBinding
data class KafkaTopicConsumerConfig(val pattern: Pattern, val props: Properties?)

@ConstructorBinding
data class KafkaStreamConfig(val pattern: Pattern, val props: Properties)



class KafkaResourceFactory(private val config: KafkaConfig) {

    val log by logger()

    val newTopics: Map<NewTopic, Properties> =
         config.topics?.values?.mapNotNull { topic ->
            topic.admin?.let { admin ->
                val props = Properties()
                props.putAll(config.consumerDefault ?: Properties())
                props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
                Pair(NewTopic(topic.name, admin.nbPartitions, admin.nbReplicas), props)
            }
        }?.toMap() ?: emptyMap()

    val consumers: Map<String, Pair<Pattern, Properties>> =
        config.consumers?.mapNotNull { consumer ->
            val props = Properties()
            props.putAll(config.consumerDefault ?: Properties())
            props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
            props.putAll(consumer.value.props ?: Properties())
            Pair(consumer.key, Pair(consumer.value.pattern, props))
        }?.toMap() ?: emptyMap()

    val producers: Map<String, Properties> =
        config.topics?.values?.mapNotNull { topic ->
            val props = Properties()
            props.putAll(config.producerDefault ?: Properties())
            props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
            topic.producer?.let { producer ->
                props.putAll(producer)
            }
            Pair(topic.name, props)
        }?.toMap() ?: emptyMap()

    val streams: Map<String, Pair<Pattern, Properties>> =
        config.streams?.mapNotNull { stream ->
            val props = Properties()
            props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
            props.putAll(stream.value.props)
            Pair(stream.key, Pair(stream.value.pattern, props))
        }?.toMap() ?: emptyMap()

    // invoked by spring on startup
    fun start() {
        buildTopicsIfRequired()
    }

    private fun buildTopicsIfRequired() {
        this.newTopics.forEach {
            val admin = Admin.create(it.value)
            log.info("Check whether ${it.key.name()} exists..")
            if (admin.listTopics().names().get().contains(it.key.name())) {
                log.info("Topic already exists: ${it.key.name()}")
            }
            else {
                log.info("Create topic: ${it.key}")
                admin.createTopics(listOf(it.key))
            }
        }
    }

    fun <K, V> newConsumer(name: String): KafkaConsumer<K, V>? =
        this.consumers[name]?.let {
            KafkaConsumer<K, V>(it.second)
        }

    fun <K, V> newProducer(name: String): KafkaProducer<K, V>? =
        this.producers[name]?.let {
            KafkaProducer<K, V>(it)
        }
}
