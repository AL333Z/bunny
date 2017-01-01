package com.al333z.bunny

import scala.concurrent.duration.{Duration, _}
import scala.language.postfixOps

case class RabbitConfig(
                         host: String,
                         vhost: String,
                         username: String,
                         password: String,
                         prefetchCount: Int = 1,
                         heartBeat: Duration = 30 seconds,
                         retryPublishDelay: FiniteDuration = 1 second,
                         retryPublishTimes: Int = 1,
                         networkRecoveryInterval: FiniteDuration = 1 second
                       )

object RabbitConfig {
  def errorQueueName(queueName: String): String = queueName + "_error"
}
