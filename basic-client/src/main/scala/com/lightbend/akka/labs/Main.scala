package com.lightbend.akka.labs

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.lighbend.akka.labs.tools.ChannelBuilderUtils
import io.akka.grpc.{ EchoClient, EchoMessage }

object Main extends App {

  implicit val system = ActorSystem()
  implicit val ctx = system.dispatcher
  implicit val mat = ActorMaterializer()


  val channel = ChannelBuilderUtils.build("127.0.0.1", 8443)
  EchoClient(channel)
    .echo(EchoMessage("the payloadContent"))
    .map(println)
    .recover { case t => t.printStackTrace() }
    .foreach { _ => System.exit(0) }

}
