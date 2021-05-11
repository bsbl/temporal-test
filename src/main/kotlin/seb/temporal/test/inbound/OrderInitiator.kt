package seb.temporal.test.inbound

import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import seb.temporal.test.glue.KafkaConfiguration
import seb.temporal.test.glue.KafkaResourceFactory
import seb.temporal.test.flows.order.OrderWorkflow
import seb.temporal.test.flows.order.OrderWorkflowImpl
import seb.temporal.test.utils.Json
import seb.temporal.test.utils.logger


interface OrderInitiator {
    fun start(order: OrderReq): String
}

class OrderInitiatorImpl(private val workflowClient: WorkflowClient, private val kafkaResourceFactory: KafkaResourceFactory): OrderInitiator {

    val log by logger()

    fun start() {
        // use stream here only for the exactly_once semantic but could be
        // standard consumer since nothing fancy's done here...
        val conf = kafkaResourceFactory.streams[KafkaConfiguration.inboundTopic] ?: error("")
        val builder = StreamsBuilder()

        val stream = builder.stream<String, String>(KafkaConfiguration.inboundTopic)
                ?: error("Stream cannot be started due to configuration issue")
        stream.foreach { key, value ->
            log.info("Processing new order: $key, $value")
            start(Json.objectMapper.readValue(value, OrderReq::class.java)) // TODO manage exception properly
        }

        val streams = KafkaStreams(builder.build(), conf.second)
        streams.start()

    }

    override fun start(order: OrderReq): String {
        // instantiate an instance of the workflow
        val instance = workflowClient.newWorkflowStub(OrderWorkflow::class.java,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(OrderWorkflowImpl.queueName)
                        .build()
        )
        //orderWorkflowClient.signalWithStart()
        val we = WorkflowClient.start(instance::acceptOrder, order)
        log.info("Workflow started: ${we.runId}")
        return we.runId
    }

}