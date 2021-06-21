package seb.temporal.test.services

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import seb.temporal.test.utils.logger
import java.util.Deque
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

@Configuration
@EnableConfigurationProperties(ChaosConfig::class)
class ChaosConfiguration {}

@ConstructorBinding
@ConfigurationProperties
data class ChaosConfig(val chaos: Map<String, ChaosExecutionConfig>)

@ConstructorBinding
data class ChaosExecutionConfig(val probability: Int)

abstract class ChaosUnit<T>(val config: ChaosExecutionConfig) {
    private val log by logger()

    private val lastBucketId = 9
    private val queues: Map<Int, Deque<T>> = ConcurrentHashMap(
        (0..lastBucketId).map {
            Pair(it, ConcurrentLinkedDeque<T>())
        }.toMap())

    private val rand = Random()

    fun crash(message: String? = null) {
        if (rand.nextInt(config.probability) == 0) {
            if (message == null)
                throw ChaosException() else
                throw ChaosException(message!!)
        }
    }

    private fun bucket(): Deque<T> =
            queues[rand.nextInt(lastBucketId)]
                    ?: error("Invalid state: simulator is not functioning properly and should be stopped immediately")

    fun offer(items: List<T>) {
        items.forEach {
            bucket().offer(it)
        }
    }

    @Scheduled(fixedDelay = 1000)
    fun releaseMessages() {
        var numberOfMessagesToRelease = rand.nextInt(10) // number of message to release
        var i = rand.nextInt(this.lastBucketId) // bucket id
        var attempts = 0
        log.trace("Release messages scheduler started: $i, $lastBucketId, $numberOfMessagesToRelease")
        //     want this amount of messages to be released
        while (numberOfMessagesToRelease > 0 &&
                // make sure to exit the loop when there's no longer message to release
                attempts < lastBucketId) {
            attempts++
            val bucketId = i%lastBucketId
            queues[bucketId]!!.pollFirst().takeIf { it != null }?.let {
                release(it)
                numberOfMessagesToRelease--
                attempts = 0
            }
        }
    }

    abstract fun release(element: T)

}

class ChaosException: RuntimeException {
    constructor(): super()
    constructor(msg: String): super(msg)
}