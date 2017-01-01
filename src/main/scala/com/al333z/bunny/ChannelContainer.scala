package com.al333z.bunny

import com.rabbitmq.client.Channel

import scala.util.Try

/**
  * A container for a channel.
  * The only purpose of this trait is to enable to distinguish between channels that (don't) support publish acks
  */
trait ChannelContainer {
  def channel: Channel
}

case class UnconfirmedChannelContainer(channel: Channel) extends ChannelContainer {
  // enable sending of sent confirmation from the broker (see: https://www.rabbitmq.com/confirms.html)
  def toConfirmedChannelContainer: Try[ConfirmedChannelContainer] = {
    Try(channel.confirmSelect())
      .map(_ => ConfirmedChannelContainer(channel))
  }
}

case class ConfirmedChannelContainer(channel: Channel) extends ChannelContainer
