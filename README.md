# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/2.4.5/maven-plugin/reference/html/)
* [Create an OCI image](https://docs.spring.io/spring-boot/docs/2.4.5/maven-plugin/reference/html/#build-image)
* [Apache Kafka Streams Support](https://docs.spring.io/spring-kafka/docs/current/reference/html/_reference.html#kafka-streams)
* [Apache Kafka Streams Binding Capabilities of Spring Cloud Stream](https://docs.spring.io/spring-cloud-stream/docs/current/reference/htmlsingle/#_kafka_streams_binding_capabilities_of_spring_cloud_stream)

### Guides
The following guides illustrate how to use some features concretely:

* [Samples for using Apache Kafka Streams with Spring Cloud stream](https://github.com/spring-cloud/spring-cloud-stream-samples/tree/master/kafka-streams-samples)

# Kafka reliability

## Broker

- unclean.leader.election.enable: false for critical transactions
- min.insync.replicas: 2 in order to reduce the risk of data loss for 
  critical transactions (to be used along with producer acks = all)
- 

## Producer

- acks = all 
- handle correctly (basically rollback the transaction or retry) exceptions 
  such as: "Leader not Available" or "NotEnoughReplicasException" to ensure the 
  message is written properly

### Retry managed by the producer

Infinite retry eventually leads to duplicates -> rely on idempotent service

- Transient (e.g. LEADER_NOT_AVAILABLE) are replayed by the producer (retries = MAX_INT or else)
- Non transient (e.g. INVALID_CONFIG) are NOT replayed by the producer

### Retry to be managed by the developer

- Nonretriable broker errors such as errors regarding message size, authorization errors, etc.
- Errors that occur before the message was sent to the broker—for example, serialization errors
  Errors that occur when the producer exhausted all retry attempts or when the available memory used 
  by the producer is filled to the limit due to using all of it to store messages while retrying

## Consumer

Rule: always commit offsets for messages after they were processed

- group.id
- auto.offset.reset: earliest or latest
- enable.auto.commit: autocommit is scheduled (auto.commit.interval.ms) vs commit manually

- pause(): in case of a failure, pausing the consumer to give time to process the event successfully
  with this method, the consumer keeps sending heartbeat to the broker so that the rebalanding does 
  not occurs 
- use a dead letter queue

# Workflow

REST -> Submit order -> publish into kafka(seb-test-order)
KStream(seb-test-order) -> create workflow(OrderWorkflow)::acceptOrder
OrderWorkflow -> {
    validateOrder
    saveOrder
} -> Persist into db

Timer(GroupWorkflow)::sendToMarket
GroupWorkflow -> {
    group
    releaseGroup
} -> send execution to market

KStream(seb-test-exec) -> create workflow(ExecutionWorkflow)::<TDB>
ExecutionWorkflow -> {
    allocate
}

# Testing the application

## Kafka

### Startup and configuration

Start Kafka

```bash
curl --silent --output docker-compose.yml \
  https://raw.githubusercontent.com/confluentinc/cp-all-in-one/6.1.1-post/cp-all-in-one/docker-compose.yml

open http://localhost:9021

# create topic:
# topic: seb-test-order-inbound
#     used to persist incoming order
#

```

Start temporal.io
```bash
git clone https://github.com/temporalio/docker-compose.git
cd docker-compose
## there's a clash on port 8088 between KSql and Temporal - changed temporal port to 9096:
## (this is just for the UI - there's no impact on SDK connection port)
vi docker-compose.yml
## start
docker-compose up

```

open http://localhost:9096/namespaces/default/workflows?range=last-30-days&status=ALL

### Shutdown:

```bash
## stop temporal:
docker-compose stop
## stop kafka:
docker-compose stop
docker system prune -a --volumes --filter "label=io.confluent.docker"
```

## Create a new order

```bash
curl -kv  -H "Content-Type: application/json" \
    -d '{"id":"1","qty":45.2,"product":{"assetClass":"Fixed Income","isin":"US0378331005"}}' \
    -X POST \
    http://localhost:8080/order/v1/new
```

