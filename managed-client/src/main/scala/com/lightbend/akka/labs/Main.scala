package com.lightbend.akka.labs

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.stream.ActorMaterializer
import com.lighbend.akka.labs.tools.ChannelBuilderUtils
import io.akka.grpc.{ Echo, EchoClient, EchoMessage }

import scala.concurrent.duration._

object Main extends App {

  implicit val system = ActorSystem()
  implicit val ctx = system.dispatcher
  implicit val mat = ActorMaterializer()

  //  private val discovery: SimpleServiceDiscovery = ServiceDiscovery(system).discovery
  private val discovery: SimpleServiceDiscovery = new HardcodedServiceDiscovery

  discovery.lookup(Echo.name, 500.millis)
    .map {
      case resolved if resolved.addresses.nonEmpty =>
        val address = resolved.addresses.head
        val channel = ChannelBuilderUtils.build(address.host, address.port.getOrElse(8443))
        EchoClient(channel)
      case r => throw new RuntimeException(s"No address available for service ${r.serviceName}")
    }
    .flatMap {
      _.echo(EchoMessage("the payloadContent"))
    }
    .map(println)
    .recover { case t => t.printStackTrace() }
    .foreach { _ => System.exit(0) }

}
