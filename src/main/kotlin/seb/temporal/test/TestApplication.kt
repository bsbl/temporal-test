package seb.temporal.test

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [CassandraAutoConfiguration::class])
class TestApplication

fun main(args: Array<String>) {
	runApplication<TestApplication>(*args)
}
