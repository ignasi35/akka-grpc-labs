package com.lightbend.akka.labs

import io.akka.grpc.{ Echo, EchoMessage }

import scala.concurrent.Future

/**
  *
  */
class EchoImpl extends  Echo {
  override def echo(in: EchoMessage): Future[EchoMessage] =
    Future.successful(in)
}
