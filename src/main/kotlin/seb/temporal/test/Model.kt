package seb.temporal.test

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import seb.temporal.test.inbound.ProductReq
import java.math.BigDecimal
import java.util.UUID

interface InboundModel {
    /**
     * Request received by the [seb.temporal.test.inbound.OrderInitiator]
     * entry point of the order management system.
     */
    data class OrderReq @JsonCreator constructor(
            @JsonProperty("inboundId") val id:      String,
            @JsonProperty("qty")       val qty:     BigDecimal,
            @JsonProperty("product")   val product: ProductReq
    )
}

interface OrderModel {
    /**
     * Event produced and emitted by the [seb.temporal.test.flows.order.OrderWorkflow]
     */
    data class Order @JsonCreator constructor(
            /**
             * The internal Id.
             */
            @JsonProperty("orderId")   val orderId:   UUID,
            /**
             * The external Id of the order provided into the client request.
             */
            @JsonProperty("inboundId") val inboundId: String,
            @JsonProperty("qty")       val qty:       BigDecimal,
            @JsonProperty("product")   val product:   ProductReq,
            /**
             * The groupId once the order is associated to a market
             * order (i.e. group)
             */
            @JsonProperty("groupId")   val groupId:   UUID? = null
    )
}

interface GroupModel {
    /**
     * Event produced and emitted by the [seb.temporal.test.flows.group.GroupWorkflow]
     */
    data class Group @JsonCreator constructor(
            @JsonProperty("groupId")   val groupId: UUID,
            @JsonProperty("orders")    val orders:  Set<OrderModel.Order>
    )
}

interface ExecutionModel {
    /**
     * Request sent to the RPC service [seb.temporal.test.services.ExecutionService]
     */
    data class ExecReq @JsonCreator constructor(
            @JsonProperty("groupId")   val groupId: UUID,
            @JsonProperty("orders")    val orders:  Set<OrderModel.Order>
    )

    /**
     * Execution message sent to the external execution system.
     * This is a simulation of an external gateway taking market
     * orders and fill them onto the market and returns the fill
     * request to the execution service through messaging.
     */
    data class ExecMessage @JsonCreator constructor(
            @JsonProperty("reqId")     val execReqId: UUID,
            @JsonProperty("groupId")   val groupId:   UUID,
            @JsonProperty("product")   val product:   ProductReq,
            @JsonProperty("qty")       val qty:       BigDecimal
    )

    /**
     * Message sent by the external execution gateway whenever a
     * market order has been filled.
     */
    data class FillExecMessage @JsonCreator constructor(
            @JsonProperty("fillId")    val fillId:    UUID,
            @JsonProperty("execReqId") val execReqId: String,
            @JsonProperty("groupId")   val groupId:   UUID,
            @JsonProperty("product")   val product:   ProductReq,
            @JsonProperty("qty")       val qty:       BigDecimal
    )

}
