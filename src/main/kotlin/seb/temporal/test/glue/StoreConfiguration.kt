package seb.temporal.test.glue

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import seb.temporal.test.store.OrderStore
import seb.temporal.test.store.OrderStoreInMemory

@Configuration
class StoreConfiguration {

    /**
     * In-memory store for the sake of demo - this of
     * course cannot be used if multiple workers are
     * started.
     */
    @Bean
    fun orderStore(): OrderStore = OrderStoreInMemory()

}