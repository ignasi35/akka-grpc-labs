package com.lightbend.akka.labrats

import java.io._

import akka.actor.{ ActorSystem, CoordinatedShutdown }
import akka.stream.ActorMaterializer
import io.akka.grpc.{ EchoClient, EchoMessage }
import io.grpc.{ Channel, ManagedChannel }
import io.grpc.netty.shaded.io.grpc.netty.{ GrpcSslContexts, NegotiationType, NettyChannelBuilder }
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext

import scala.util.Failure

object Main extends App {


  implicit val system = ActorSystem()
  implicit val ctx = system.dispatcher
  implicit val mat = ActorMaterializer()

  println("Building channel...")
  var channel: ManagedChannel = ChannelBuilderUtils.build()

  println("Channel is built. Starting Client...")
  val client = EchoClient(channel)

  client
    .echo(EchoMessage("the payloadContent"))
    .map(println)
    .onComplete {
      case Failure(t) =>
        t.printStackTrace()
        System.exit(0)
      case _ =>
        System.exit(0)
    }
}

private class PlaceHolder

private object ChannelBuilderUtils {

  def build() = {

    val sslContext: SslContext =
      try
        GrpcSslContexts.forClient.trustManager(loadCert("ca.pem")).build
      catch {
        case ex: Exception => throw new RuntimeException(ex)
      }

    val builder =
      NettyChannelBuilder
        .forAddress("127.0.0.1", 8443)
        .flowControlWindow(65 * 1024)
        .negotiationType(NegotiationType.TLS)
        .sslContext(sslContext)

          builder.overrideAuthority("foo.test.google.fr")

    builder.build
  }

  def loadCert(name: String): File = {
    val in = new BufferedInputStream(classOf[PlaceHolder].getResourceAsStream("/certs/" + name))
    val tmpFile: File = File.createTempFile(name, "")
    tmpFile.deleteOnExit()
    val os = new BufferedOutputStream(new FileOutputStream(tmpFile))
    try {
      var b = 0
      do {
        b = in.read
        if (b != -1)
          os.write(b)
        os.flush()
      } while (b != -1)
    } finally {
      in.close()
      os.close()
    }
    tmpFile
  }

}