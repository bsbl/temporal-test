package seb.temporal.test.glue

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import seb.temporal.test.store.OrderStore
import seb.temporal.test.store.OrderStoreInMemory

@Configuration
class StoreConfiguration {

    /**
     * Use in-memory store as of now
     */
    @Bean
    fun orderStore(): OrderStore = OrderStoreInMemory()

}