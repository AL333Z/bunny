import java.util.concurrent.{Executors, ThreadFactory}

import com.al333z.bunny.BunnyChannelFactory.Binding
import com.al333z.bunny.{BunnyChannelFactory, RabbitConfig}
import com.rabbitmq.client.{Channel, ConnectionFactory}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.FunSuite

import scala.util.Try

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
    assert(doesExchangeExist(setupChannel, "e"))
    setupChannel.close()

    // run
    val tryChannel = BunnyChannelFactory(rabbitConfig)
      .forConsumer(
        queueName = "q",
        exchangeName = "e",
        routingKey = "k"
      )

    // assertions
    assert(tryChannel.isSuccess)
    val channel = tryChannel.get

    assert(doesQueueExist(channel, "q"))
    assert(doesBindingExist(channel, "q", "e", "k"))

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
    assert(doesExchangeExist(setupChannel, "e"))
    assert(doesExchangeExist(setupChannel, "e1"))
    setupChannel.close()

    // run
    val tryChannel = BunnyChannelFactory(rabbitConfig)
      .forConsumer(
        queueName = "q",
        bindings = Binding("q", "e", "k", Map()), Binding("q", "e1", "k", Map())
      )

    // assertions
    assert(tryChannel.isSuccess)
    val channel = tryChannel.get

    assert(doesQueueExist(channel, "q"))
    assert(doesBindingExist(channel, "q", "e", "k"))
    assert(doesBindingExist(channel, "q", "e1", "k"))

    // cleanup
    channel.queueDelete("q")
    channel.exchangeDelete("e")
    channel.exchangeDelete("e1")
    channel.close()
  }

  private def doesExchangeExist(channel: Channel, exchangeName: String): Boolean = {
    Try(channel.exchangeDeclarePassive(exchangeName)).isSuccess
  }

  private def doesQueueExist(channel: Channel, queueName: String): Boolean = {
    Try(channel.queueDeclarePassive(queueName)).isSuccess
  }

  private def doesBindingExist(channel: Channel, queueName: String, exchangeName: String, routingKey: String): Boolean = {
    // this is a kind of tricky
    val res = Try(channel.queueUnbind(queueName, exchangeName, routingKey)).isSuccess
    channel.queueBind(queueName, exchangeName, routingKey)
    res
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

    val connection = factory.newConnection()
    val channel = connection.createChannel()
    channel.basicQos(rabbitConfig.prefetchCount)

    channel
  }

}
