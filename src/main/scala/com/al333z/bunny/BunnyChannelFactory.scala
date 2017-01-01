package com.al333z.bunny

import java.util.concurrent.{Executors, ThreadFactory}

import com.al333z.bunny.BunnyChannelFactory.{Binding, Exchange, Queue}
import com.rabbitmq.client.{Channel, ConnectionFactory}

import scala.collection.JavaConverters._
import scala.util.Try

class BunnyChannelFactory private(
                                    config: RabbitConfig,
                                    queue: Option[Queue] = None,
                                    exchange: Option[Exchange] = None,
                                    bindings: List[Binding] = List()
                                  ) {
  /**
    * Instruct the factory to create the `queueName` queue and the binding from the `exchangeName` exchange to the
    * `queueName` queue, using `routingKey`.
    */
  def forConsumer(
                   queueName: String,
                   exchangeName: String,
                   routingKey: String,
                   args: Map[String, AnyRef] = Map()
                 ): Try[Channel] = {
    forConsumer(queueName, Binding(queueName, exchangeName, routingKey, args))
  }

  /**
    * Instruct the factory to create the `queueName` queue and the bindings in `bindings` to the `queueName` queue.
    */
  def forConsumer(queueName: String, bindings: Binding*): Try[Channel] = {
    new BunnyChannelFactory(
      config = config,
      queue = Some(Queue(queueName)),
      bindings = bindings.toList
    ).build
  }

  /**
    * Instruct the factory to create the `exchangeName` exchange.
    *
    * NB: each publish won't be awaited for confirmation.
    */
  def forProducerUnconfirmed(exchangeName: String, exchangeType: String): Try[UnconfirmedChannelContainer] = {
    new BunnyChannelFactory(
      config = config,
      exchange = Some(Exchange(exchangeName, exchangeType))
    )
      .build
      .map(UnconfirmedChannelContainer)
  }

  /**
    * Instruct the factory to create the `exchangeName` exchange, the `queueName` name, and the binding using the
    * `routingKey` routing key.
    *
    * This may be used for error queues that require explicitly an error queue bound to an exchange.
    *
    * NB: each publish won't be awaited for confirmation.
    */
  def forProducerUnconfirmedWithQueueBound(
                                            queueName: String,
                                            exchangeName: String,
                                            routingKey: String,
                                            args: Map[String, AnyRef] = Map()
                                          ): Try[UnconfirmedChannelContainer] = {
    new BunnyChannelFactory(
      config = config,
      queue = Some(Queue(queueName)),
      exchange = Some(Exchange(exchangeName, "topic")),
      bindings = List(Binding(queueName, exchangeName, routingKey, args))
    )
      .build
      .map(UnconfirmedChannelContainer)
  }

  /**
    * Instruct the factory to create the `exchangeName` exchange.
    *
    * NB: each publish will be awaited for confirmation.
    */
  def forProducer(exchangeName: String, exchangeType: String): Try[ConfirmedChannelContainer] = {
    forProducerUnconfirmed(exchangeName, exchangeType)
      .flatMap(_.toConfirmedChannelContainer)
  }

  /**
    * Instruct the factory to create the `exchangeName` exchange, the `queueName` name, and the binding using the
    * `routingKey` routing key.
    *
    * This may be used for error queues that require explicitly an error queue bound to an exchange.
    *
    * NB: each publish will be awaited for confirmation.
    */
  def forProducerWithQueueBound(
                                 queueName: String,
                                 exchangeName: String,
                                 routingKey: String,
                                 args: Map[String, AnyRef] = Map()
                               ): Try[ConfirmedChannelContainer] = {
    forProducerUnconfirmedWithQueueBound(queueName, exchangeName, routingKey, args)
      .flatMap(_.toConfirmedChannelContainer)
  }

  private def build: Try[Channel] = {
    for {
      channel <- createChannel(config)
      _ <- createQueues(channel)
      _ <- createExchanges(channel)
      _ <- createBindings(channel)
    } yield channel
  }

  private def createBindings(channel: Channel) = Try {
    bindings
      .filterNot(_.exchangeName.isEmpty)
      .foreach { b =>
        //        logger.info(s"Binding exchange:[${b.exchangeName}] -> queue:[${b.queueName}] with key:[${b.routingKey}]")
        channel.queueBind(b.queueName, b.exchangeName, b.routingKey, b.args.asJava)
      }
  }

  private def createQueues(channel: Channel) = Try {
    queue.foreach(q => {
      //      logger.info(s"Creating rabbit queue: ${q.name} on [${config.host}]")
      channel.queueDeclare(q.name, true, false, false, null)
    })
  }

  private def createExchanges(channel: Channel) = Try {
    exchange
      .filterNot(_.name.isEmpty)
      .foreach(q => {
        //        logger.info(s"Creating rabbit exchange: [${q.name},${q.routingKey}] on [${config.host}]")
        channel.exchangeDeclare(q.name, q.routingKey, true)
      })
  }

  private def createChannel(rabbitConfig: RabbitConfig): Try[Channel] = Try {

    val factory = new ConnectionFactory()
    factory.setHost(rabbitConfig.host)
    factory.setUsername(rabbitConfig.username)
    factory.setPassword(rabbitConfig.password)
    factory.setVirtualHost(rabbitConfig.vhost)
    factory.setAutomaticRecoveryEnabled(true)
    factory.setNetworkRecoveryInterval(rabbitConfig.networkRecoveryInterval.toMillis)
    factory.setRequestedHeartbeat(rabbitConfig.heartBeat.toSeconds.toInt)

    factory.setThreadFactory(new ThreadFactory {
      val defaultThreadFactory: ThreadFactory = Executors.defaultThreadFactory()

      override def newThread(r: Runnable): Thread = {
        val thread = defaultThreadFactory.newThread(r)
        thread.setDaemon(true) // make the internal thread of RabbitMQ library doesn't prevent the JVM from exiting
        thread
      }
    })

    //    logger.info(s"Connecting to rabbit on: ${rabbitConfig.host}/${rabbitConfig.vhost} with " +
    //      s"prefetchCount ${rabbitConfig.prefetchCount}, heartbeat ${rabbitConfig.heartBeat}")
    val connection = factory.newConnection()
    val channel = connection.createChannel()
    channel.basicQos(rabbitConfig.prefetchCount)

    channel
  }
}

/**
  * Companion object for `RabbitChannelFactory`
  */
object BunnyChannelFactory {

  def apply(config: RabbitConfig) = new BunnyChannelFactory(config)

  case class Binding(queueName: String, exchangeName: String, routingKey: String, args: Map[String, AnyRef])

  private case class Exchange(name: String, routingKey: String)

  private case class Queue(name: String)

}
