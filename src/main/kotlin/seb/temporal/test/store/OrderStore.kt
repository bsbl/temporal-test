package seb.temporal.test.store

import seb.temporal.test.OrderModel
import seb.temporal.test.inbound.OrderReq
import seb.temporal.test.utils.logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

interface OrderStore {
    /**
     * Save an order into the store
     */
    fun save(order: OrderModel.Order)

    /**
     * @return orders matching the collection of [orderIds]
     */
    fun get(orderIds: Collection<UUID>): Set<OrderModel.Order>

    /**
     * @return pending orders (not assigned to any group)
     */
    fun pending(): Set<OrderModel.Order>

    /**
     * Attach all the pending orders to the specified [groupId]
     * If the group id is already registered (replay) then the
     * group is not modified i.e. no new order attached to it
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
     * groups by groupId
     */
    private val groups = ConcurrentHashMap<UUID, GroupInternal>()

    override fun save(order: OrderModel.Order) {
        orderStore.computeIfAbsent(order.orderId) {
            OrderInternal(order)
        }
    }


    override fun get(orderIds: Collection<UUID>): Set<OrderModel.Order> =
        orderIds.mapNotNull {
            orderStore[it]?.order
        }.toSet()


    override fun pending(): Set<OrderModel.Order> = orderStore
            .filter { it.value.groupId.get() == null }
            .map { it.value.order }
            .toSet()


    override fun groupPendingOrders(groupId: UUID): Set<UUID> {
        // idempotent
        if (groups[groupId] != null) {
            return orderStore.filter { it.value.groupId.get() == groupId }
                    .map { it.key }
                    .toSet()
        }
        this.groups[groupId] = GroupInternal(groupId)
        return orderStore.mapNotNull {
            if (it.value.groupId.compareAndSet(null, groupId)) it.key else null
        }.toSet()
    }

}

data class OrderInternal(val order:OrderModel.Order, var groupId:AtomicReference<UUID?> = AtomicReference(null))
data class GroupInternal(val groupId: UUID)
