# bunny [![Build Status](https://travis-ci.org/AL333Z/bunny.svg?branch=master)](https://travis-ci.org/AL333Z/bunny)

## Rationale

This tiny lib has only one purpose: simplify the life of the developer when creating a RabbitMQ `Channel`.
The assumptions are:
- a **consumer owns a queue**, so it'll need to create a queue and the corresponding binding(s) from one or multiple exchange(s)
- a **producer owns an exchange**, so it'll need to create an exchange

This library will give you a few methods to do this, whitout any further boilerplate.

## Samples

```scala
val rabbitConfig = RabbitConfig(
    host = "localhost",
    vhost = "/",
    username = "guest",
    password = "guest"
)

val consumingChannel = BunnyChannelFactory(rabbitConfig)
      .forConsumer(
        queueName = "q",
        exchangeName = "e",
        routingKey = "k"
)

val producingChannel = BunnyChannelFactory(rabbitConfig)
      .forProducer(
        exchangeName = "e",
        exchangeType = "topic"
)

// your code that use the channels..
```

For more, take a look at the [tests](https://github.com/AL333Z/bunny/blob/master/src/test/scala/BunnyChannelFactoryTest.scala).

## Docs
The methods that `BunnyChannelFactory` offers are:

- `forConsumer(queueName: String, exchangeName: String, routingKey: String, args: Map[String, AnyRef] = Map()): Try[Channel]`, which will create a queue and the binding to the exchanges provided.
- `def forConsumer(queueName: String, bindings: Binding*): Try[Channel]`, which is like the previous, but with only one exchange.
- `def forProducer(exchangeName: String, exchangeType: String): Try[ConfirmedChannelContainer]`, which will create a new exchange in which a producer can push messages. The channel which is returned supports [publisher confirms](http://www.rabbitmq.com/blog/2011/02/10/introducing-publisher-confirms/).
-  `def forProducerUnconfirmed(exchangeName: String, exchangeType: String): Try[UnconfirmedChannelContainer]`, like the previous without the publisher confirms turned on.
- other additional two methods `forProducerWithQueueBound` and `forProducerUnconfirmedWithQueueBound`, which are like the previous but will also create a binding to a queue. One use case for these two are for supporting error queues.

## Installation

```
libraryDependencies += "com.al333z" %% "bunny" % "0.0.4"
```
