package com.lightbend.akka.labs

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.stream.ActorMaterializer
import com.lighbend.akka.labs.tools.ChannelBuilderUtils
import com.lightbend.akka.labs.utils.HardcodedServiceDiscovery
import io.akka.grpc.{ Echo, EchoClient, EchoMessage }
import io.grpc.ManagedChannel

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Given a ServiceDiscovery, builds a channelFactory that is injected into amn
  * implementation of the gRPC Service. The user gets a plain gRPC interface that
  * internally will create a new channel and a new client for each call.
  * This implementation only works for unary calls.
  */
object ChannelPerCall extends App {

  implicit val system = ActorSystem()
  implicit val ctx = system.dispatcher
  implicit val mat = ActorMaterializer()

  // using a hardcoded ServiceDiscovery to ease testing
  private val discovery: SimpleServiceDiscovery = new HardcodedServiceDiscovery

  val channelFactory: () => Future[ManagedChannel] = () => {
    discovery.lookup(Echo.name, 500.millis)
      .map {
        case resolved if resolved.addresses.nonEmpty =>
          val address = resolved.addresses.head
          ChannelBuilderUtils.build(address.host, address.port.getOrElse(8443))
        case r => throw new RuntimeException(s"No address available for service ${r.serviceName}")
      }
  }

  // This builds an implementation of the gRPC trait with a channelFactory that will create a new Channel
  // for every call. This is very inefficient
  val client: Echo = new EchoChannelPerCallClient(channelFactory)

  private val seq: Seq[Future[Unit]] = (0 to 9999).map { id =>
    client
      .echo(EchoMessage(s"The payload for $id"))
      .map(println)
  }
  Future.sequence(seq)
    .recover { case t => t.printStackTrace() }
    .foreach { _ => System.exit(0) }

}

import akka.stream.Materializer

import scala.concurrent.{ ExecutionContext, Future }

class EchoChannelPerCallClient(channelFactory: () => Future[ManagedChannel])(implicit mat: Materializer, ctx: ExecutionContext) extends Echo {

  override def echo(in: EchoMessage): Future[EchoMessage] = withChannel { ch =>
    EchoClient(ch).echo(in)
  }

  private def withChannel[T](block: (ManagedChannel) => Future[T]): Future[T] = {
    val channel = channelFactory()
    channel
      .flatMap { ch =>
        block(ch)
          .transform {
            x => {
              ch.shutdown()
              x
            }
          }
      }
  }
}