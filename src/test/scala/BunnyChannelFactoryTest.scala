import java.util.concurrent.TimeoutException

import com.al333z.bunny.BunnyChannelFactory.Binding
import com.al333z.bunny.{BunnyChannelFactory, RabbitConfig}
import com.rabbitmq.client.{Channel, ConnectionFactory}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.FunSuite

import scala.util.{Success, Try}

class BunnyChannelFactoryTest extends FunSuite {

  val config: Config = ConfigFactory.load()

  val rabbitConfig = RabbitConfig(
    host = config.getString("rabbit-host"),
    vhost = config.getString("rabbit-vhost"),
    username = config.getString("rabbit-username"),
    password = config.getString("rabbit-password")
  )

  test("forConsumer") {

    // setup
    val setupChannel = createChannel(rabbitConfig).get
    createExchanges(setupChannel, "e", "topic").get
    assert(exchangeExist(setupChannel, "e"))
    setupChannel.close()

    // run
    val channel = BunnyChannelFactory(rabbitConfig)
      .forConsumer(
        queueName = "q",
        exchangeName = "e",
        routingKey = "k"
      ).get

    // assertions
    assert(queueExist(channel, "q"))
    assert(bindingExist(channel, "q", "e", "k"))

    // cleanup
    channel.queueDelete("q")
    channel.exchangeDelete("e")
    channel.close()
  }

  test("forConsumer with multiple bindings") {

    // setup
    val setupChannel = createChannel(rabbitConfig).get
    createExchanges(setupChannel, "e", "topic").get
    createExchanges(setupChannel, "e1", "topic").get
    assert(exchangeExist(setupChannel, "e"))
    assert(exchangeExist(setupChannel, "e1"))
    setupChannel.close()

    // run
    val channel = BunnyChannelFactory(rabbitConfig)
      .forConsumer(
        queueName = "q",
        bindings = Binding("q", "e", "k", Map()), Binding("q", "e1", "k", Map())
      ).get

    // assertions
    assert(queueExist(channel, "q"))
    assert(bindingExist(channel, "q", "e", "k"))
    assert(bindingExist(channel, "q", "e1", "k"))

    // cleanup
    channel.queueDelete("q")
    channel.exchangeDelete("e")
    channel.exchangeDelete("e1")
    channel.close()
  }

  test("forProducerUnconfirmed") {

    // run
    val channel = BunnyChannelFactory(rabbitConfig)
      .forProducerUnconfirmed(
        exchangeName = "e",
        exchangeType = "topic"
      ).get.channel

    // assertions
    assert(exchangeExist(channel, "e"))
    assert(!isWaitingForConfirms(channel))

    // cleanup
    channel.exchangeDelete("e")
    channel.close()
  }

  test("forProducerUnconfirmedWithQueueBound") {

    // run
    val channel = BunnyChannelFactory(rabbitConfig)
      .forProducerUnconfirmedWithQueueBound(
        queueName = "q",
        exchangeName = "e",
        routingKey = "k"
      ).get.channel

    // assertions
    assert(exchangeExist(channel, "e"))
    assert(queueExist(channel, "q"))
    assert(bindingExist(channel, "q", "e", "k"))
    assert(!isWaitingForConfirms(channel))

    // cleanup
    channel.exchangeDelete("e")
    channel.queueDelete("q")
    channel.close()
  }

  test("forProducer") {

    // run
    val channel = BunnyChannelFactory(rabbitConfig)
      .forProducer(
        exchangeName = "e",
        exchangeType = "topic"
      ).get.channel

    // assertions
    assert(exchangeExist(channel, "e"))
    assert(isWaitingForConfirms(channel))

    // cleanup
    channel.exchangeDelete("e")
    channel.close()
  }

  test("forProducerWithQueueBound") {

    // run
    val channel = BunnyChannelFactory(rabbitConfig)
      .forProducerWithQueueBound(
        queueName = "q",
        exchangeName = "e",
        routingKey = "k"
      ).get.channel

    // assertions
    assert(exchangeExist(channel, "e"))
    assert(queueExist(channel, "q"))
    assert(bindingExist(channel, "q", "e", "k"))
    assert(isWaitingForConfirms(channel))

    // cleanup
    channel.exchangeDelete("e")
    channel.queueDelete("q")
    channel.close()
  }

  private def exchangeExist(channel: Channel, exchangeName: String): Boolean = {
    Try(channel.exchangeDeclarePassive(exchangeName)).isSuccess
  }

  private def queueExist(channel: Channel, queueName: String): Boolean = {
    Try(channel.queueDeclarePassive(queueName)).isSuccess
  }

  private def bindingExist(channel: Channel, queueName: String, exchangeName: String, routingKey: String): Boolean = {
    // not ideal, but it's only a test
    val res = Try(channel.queueUnbind(queueName, exchangeName, routingKey)).isSuccess
    channel.queueBind(queueName, exchangeName, routingKey)
    res
  }

  private def isWaitingForConfirms(channel: Channel): Boolean = {
    Try(channel.waitForConfirms(100))
      .map(_ => ())
      .recoverWith {
        case _: TimeoutException => Success(()) // ignore timeouts, we only care about InvalidStateException
      }
      .isSuccess
  }

  private def createExchanges(channel: Channel, name: String, exchangeType: String) = Try {
    channel.exchangeDeclare(name, exchangeType, true)
  }

  private def createChannel(rabbitConfig: RabbitConfig): Try[Channel] = Try {

    val factory = new ConnectionFactory()
    factory.setHost(rabbitConfig.host)
    factory.setUsername(rabbitConfig.username)
    factory.setPassword(rabbitConfig.password)
    factory.setVirtualHost(rabbitConfig.vhost)
    factory.setNetworkRecoveryInterval(rabbitConfig.networkRecoveryInterval.toMillis)
    factory.setRequestedHeartbeat(rabbitConfig.heartBeat.toSeconds.toInt)

    val connection = factory.newConnection()
    val channel = connection.createChannel()
    channel.basicQos(rabbitConfig.prefetchCount)

    channel
  }

}
