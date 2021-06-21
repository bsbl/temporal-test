package seb.temporal.test.services

import io.temporal.client.WorkflowClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@Configuration
class ServiceConfiguration {

    @Bean
    fun executionService(chaosConfig: ChaosConfig, workflowClient: WorkflowClient): ExecutionService =
            ExecutionServiceImpl(chaosConfig, workflowClient)

}