package seb.temporal.test.inbound

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.temporal.api.enums.v1.WorkflowIdReusePolicy
import io.temporal.api.workflowservice.v1.StartWorkflowExecutionRequest
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowExecutionAlreadyStarted
import io.temporal.client.WorkflowOptions
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import org.apache.kafka.clients.producer.KafkaProducer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import seb.temporal.test.glue.KafkaConfiguration
import seb.temporal.test.glue.KafkaResourceFactory
import seb.temporal.test.utils.logger
import seb.temporal.test.utils.send
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.TimeUnit


@RestController
@RequestMapping("order")
class RestController(@Autowired private val workflowClient: WorkflowClient,
                     @Autowired private val kafkaResourceFactory: KafkaResourceFactory) {

    private val log by logger()

    private val queueName = "order_q";

    // force non null / required as per configuration
    private val producer: KafkaProducer<String, String> = kafkaResourceFactory.newProducer(KafkaConfiguration.eventInboundTopic)!!

    init {
        log.info("test: ${Uuids.timeBased()}")
    }

    @PostMapping("v1/new")
    fun new(@RequestBody order: OrderReq): String {
        val key = order.id
        log.info("Start a new workflow instance for order ${order.id}")
        val response = startOrderProcessing(order)
        if (response == "OK") {
            log.info("Publish message with orderId $key")
            val future = producer.send(KafkaConfiguration.eventInboundTopic, key, order)
            log.info("Waiting (until retries finish) for ack(s) with orderId $key")
            val record = future.get()
            log.info("Message published successfully for orderId $key, record=$record")
        }
        return response
    }

    /**
     * This demonstrate how to start a new workflow instance
     * in a decoupled way - the workflow implementation is not
     * in the same microservice (as well as the worker).
     * The workflowType (OrderWorkflow) is the name of the
     * class annotated with @WorkflowMethod which holds the
     * workflow implementation.
     * Another option would be to hold the workflow implementation
     * here along with the REST interface and to invoke "remote"
     * activities hosted in different microservices.
     * This will be demonstrated later in the flow.
     */
    private fun startOrderProcessing(order: OrderReq): String {
        val options = WorkflowOptions.newBuilder()
                .setTaskQueue(queueName)
                .setWorkflowId(order.id)
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                .setWorkflowExecutionTimeout(Duration.ofDays(30))
                .build()
        // Create the workflow client stub
        val orderWorkflow = workflowClient.newUntypedWorkflowStub("OrderWorkflow-v1", options)
        return try {
            orderWorkflow.start(order)
            "OK";
        }
        catch (ex: WorkflowExecutionAlreadyStarted) {
            log.warn("Workflow for this order is already in the system: ${order.id}")
            "ALREADY_STARTED"
        }
        catch (ex: RuntimeException) {
            log.error("Unable to start the workflow for this order: ${order.id}")
            throw ex
        }
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