package seb.temporal.test.store

import com.datastax.oss.driver.api.core.uuid.Uuids
import seb.temporal.test.inbound.OrderReq
import seb.temporal.test.utils.logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

interface OrderStore {
    /**
     * Save an order into the store
     * @return the internal orderId
     */
    fun save(order: OrderReq): UUID

    /**
     * @return orders matching the collection of [orderIds]
     */
    fun get(orderIds: Collection<UUID>): Set<OrderReq>

    /**
     * @return pending orders (not assigned to any group)
     */
    fun pending(): Set<OrderReq>

    /**
     * Attach all the pending orders to the specified [groupId]
     * @return the newly attached orderIds
     */
    fun groupPendingOrders(groupId: UUID): Set<UUID>
}


/**
 * For local testing purpose only
 */
class OrderStoreInMemory: OrderStore {

    private val log by logger()

    /**
     * internal orderId to [OrderReq]
     */
    private val orderStore = ConcurrentHashMap<UUID, OrderInternal>()

    /**
     * [OrderReq.id] to internal orderId
     */
    private val orderIdToStoreId = ConcurrentHashMap<String, UUID>()

    /**
     * groups by groupId
     */
    private val groups = ConcurrentHashMap<UUID, GroupInternal>()

    override fun save(order: OrderReq): UUID =
        orderIdToStoreId.computeIfAbsent(order.id) {
            val id = Uuids.timeBased()
            orderStore[id] = OrderInternal(order)
            id
        }


    override fun get(orderIds: Collection<UUID>): Set<OrderReq> =
        orderIds.mapNotNull {
            orderStore[it]?.order
        }.toSet()


    override fun pending(): Set<OrderReq> = orderStore
            .filter { it.value.groupId.get() == null }
            .map { it.value.order }
            .toSet()


    override fun groupPendingOrders(groupId: UUID): Set<UUID> {
        if (groups[groupId] != null) {
            log.warn("The group has already been released - sounds like a replay or duplicate event: $groupId")
            return orderStore.filter { it.value.groupId.get() == groupId }
                    .mapNotNull { orderIdToStoreId[it.value.order.id] }
                    .toSet()
        }
        this.groups[groupId] = GroupInternal(groupId)
        return orderStore.mapNotNull {
            if (it.value.groupId.compareAndSet(null, groupId)) it.key else null
        }.toSet()
    }

}

data class OrderInternal(val order:OrderReq, var groupId:AtomicReference<UUID?> = AtomicReference(null))
data class GroupInternal(val groupId: UUID)
