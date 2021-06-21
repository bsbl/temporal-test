# Getting Started

**THIS IS WORK IN PROGRESS SINCE I DON'T HAVE MUCH TIME TO PROGRESS QUICKLY ON THIS PROJECT**

## Quick start

- Start kafka cluster: docker-compose up -d from ./docker/kafka
- Start temporal cluster: docker-compose up -d from ./docker/temporal.io
- Kafka console: http://localhost:9021/
- Temporal console: http://localhost:9096/

### Reference Documentation

For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/2.4.5/maven-plugin/reference/html/)
* [Create an OCI image](https://docs.spring.io/spring-boot/docs/2.4.5/maven-plugin/reference/html/#build-image)
* [Apache Kafka Streams Support](https://docs.spring.io/spring-kafka/docs/current/reference/html/_reference.html#kafka-streams)
* [Apache Kafka Streams Binding Capabilities of Spring Cloud Stream](https://docs.spring.io/spring-cloud-stream/docs/current/reference/htmlsingle/#_kafka_streams_binding_capabilities_of_spring_cloud_stream)
* [Temporal.io sample (java)]()

### Guides

The following guides illustrate how to use some features concretely:

* [Samples for using Apache Kafka Streams with Spring Cloud stream](https://github.com/spring-cloud/spring-cloud-stream-samples/tree/master/kafka-streams-samples)
* [Take care to the Temporal.io common mistakes](https://community.temporal.io/t/signaling-activity-from-workflow/1079/3)



# Workflow

## List of temporal.io features to be tested

| Feature                           | Tested | Location                                                                                 |
| :-------------------------------- | :----: | :--------------------------------------------------------------------------------------- |
| Decouple instantiater and handler |   Y    | [REST inbound](src/main/kotlin/seb/temporal/test/inbound/Rest.kt)                        |
| Retry                             |   Y    | [chaos feature](src/main/kotlin/seb/temporal/test/services/ExecutionService.kt)          |
| Query                             |   Y    | [list of orders](src/main/kotlin/seb/temporal/test/flows/exec/ExecutionWorkflow.kt)      |
| Scheduled                         |   Y    | [group time base cut-off](src/main/kotlin/seb/temporal/test/flows/group/GroupStarter.kt) |
| Signals to handle many to one     |   Y    | [allocations](src/main/kotlin/seb/temporal/test/flows/exec/ExecutionWorkflow.kt)         |
| Heartbeat/long running activity   |   N    | TODO                                                                                     |
| Timeout                           |   N    | TODO                                                                                     |
| Child workflow                    |   N    | TODO                                                                                     |
| Cancellation scope / async        |   N    | TODO                                                                                     |
| SAGA                              |   N    | TODO                                                                                     |
| Unit testing                      |   N    |                                                                                          |


Recommeneded readings:
- https://community.temporal.io/t/how-to-get-the-activity-state-at-the-workflow-level/1292
- https://community.temporal.io/t/limit-workflow-based-concurrent-executions/633
- https://community.temporal.io/t/workflow-await-does-it-release-thread/252
- https://community.temporal.io/t/aggregating-many-json-files-into-parquet/1111
- https://community.temporal.io/t/possibility-of-scalable-buffered-counter/152
- https://community.temporal.io/t/workflows-variables-activities/834
- https://github.com/temporalio/sdk-java/issues/214



### Decouple instantiater and handler

Recommended readings:
- https://community.temporal.io/t/springboot-microservices-managed-by-temporal-io-rabbitmq/1489


### Retry

### Query

### Scheduled

### Signals to handle many to one

recommended readings:
- https://community.temporal.io/t/polling-in-workflow-vs-activity
- https://community.temporal.io/t/synchronizing-signal-method-executions/642
- https://github.com/temporalio/samples-java/blob/master/src/main/java/io/temporal/samples/hello/HelloSignal.java
- https://community.temporal.io/t/continueasnew-signals


Signals are executed in separate threads so there's no synchronization between calls.
For this reason a signal should never call activities.
The approach is to fill a collection with events and to poll events from the workflow method to process them one by one.
Since signals can occur in parallel, a concurrent data structure has to be used.
I a queue is needed, prefer using the ```WorkflowQueue``` since it has some internal logic to deal with workflow constraints.

Many to one with signal is implemented in the Execution workflow where [multiple fill requests are received and trigger allocation and booking](src/main/kotlin/seb/temporal/test/flows/exec/ExecutionWorkflow.kt)

As per [continueasnew-signals](https://community.temporal.io/t/continueasnew-signals) depending on how the workflow is implememted,
signals can be lost when continueAsNew is invoked and a signal has not been processed.



### Heartbeat/long running activity

- https://community.temporal.io/t/long-running-workflows-activities/1701


### Timeout


### Child workflow


### Cancellation scope / async


### SAGA


## Description of the workflows in this test project

REST -> Submit order -> emit event (OrderReq) into kafka(seb-test-inbound)
KStream(seb-test-inbound-gw) -> create workflow(OrderWorkflow)::acceptOrder
OrderWorkflow -> {
    validateOrder
    saveOrder
} -> Persist into db + emit event (OrderReq) into seb-test-order

Timer(GroupWorkflow)::sendToMarket
GroupWorkflow -> {
    group
    releaseGroup
} -> send Group to the execution component (ExecReq) + emit event (Group) into seb-test-group
The call to the Execution component is a synchronous RPC call
This could be REST or whatever. Here, I use local JVM invocation
as a first version since the point here is to validate that
failure in synchronous activity can be replayed and resumed
successfully by Temporal (most critical feature of the workflow
isn't it?)

The RPC call crashes randomly based on a probability configured
in yaml ${chaos.execution.probability}

In case of success, a message is published in Kafka (seb-test-exec-ext)
to simulate a message sent to external system communicating by message.
This external component returns partial executions through
kafka message in the seb-test-exec-gw


KStream(seb-test-exec-gw) -> create workflow if not exist for the execution id(ExecutionWorkflow)::<TDB>
ExecutionWorkflow -> {
    allocate
    book
}

both allocation and booking as part of the workflow can be rest calls
or any RPC invocation style.
The crashing probability can be configured with ${chaos.allocation.probability}
and ${chaos.booking.probability}

There's one workflow instance per execution Id.
The workflow is terminated once the whole execution is allocated and booked.


# Temporal

Very good post for those familiar with Akka or actor model:
https://manuel.bernhardt.io/2021/04/12/tour-of-temporal-welcome-to-the-workflow


## Design recommendation

https://community.temporal.io/t/purpose-of-child-workflows/652
https://community.temporal.io/t/springboot-microservices-managed-by-temporal-io-rabbitmq/1489/3

Check also the java doc within the project since it contains some link to HTTP resources

## Error handling in Temporal

https://community.temporal.io/t/when-a-workflow-is-cancelled-how-is-the-ongoing-execution-handled/62
https://community.temporal.io/t/questions-around-activity-errors-retry-and-more-complex-error-handling-scenarios/2016
https://github.com/temporalio/sdk-java/blob/30420212a26283cf7cbe6339512058a3186074e8/temporal-sdk/src/main/java/io/temporal/failure/ApplicationFailure.java#L84


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

- Non-retriable broker errors such as errors regarding message size, authorization errors, etc.
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




# Testing the application

## Kafka

### Startup and configuration

Start Kafka

```bash
cd docker/kafka
# curl --silent --output docker-compose.yml \
#  https://raw.githubusercontent.com/confluentinc/cp-all-in-one/6.1.1-post/cp-all-in-one/docker-compose.yml

docker-compose up -d
open http://localhost:9021

# create topic:
# topic: seb-test-order-inbound
#     used to persist incoming order
#

```

Start temporal.io
```bash
# git clone https://github.com/temporalio/docker-compose.git
cd docker/temporal.io
## there's a clash on port 8088 between KSql and Temporal - changed temporal port to 9096:
## (this is just for the UI - there's no impact on SDK connection port)
vi docker-compose.yml
## start
docker-compose up -d

open http://localhost:9096/namespaces/default/workflows?range=last-30-days&status=ALL
```

### Start the spring-boot app
```bash
mvn clean package
export TEMPORAL_DEBUG=true
java -jar target/test-0.0.1-SNAPSHOT.jar

```

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


## CLI

```bash
# list 20 open workflows of type 'OrderWorkflow-v1'
tctl wf l --op --wt OrderWorkflow-v1 --ps 20

# list 1 open workflow instances of type 'OrderWorkflow-v1' and terminate it
tctl wf l --op --wt OrderWorkflow-v1 --ps 1 | \
     grep --line-buffered -v "WORKFLOW ID" | \
     cut -d'|' -f2 | \
     awk '{ print("Terminate workflowId:", $1); system("/usr/local/bin/tctl wf term -w "$1); }'
```

