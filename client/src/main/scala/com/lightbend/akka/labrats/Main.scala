package com.lightbend.akka.labrats

import java.io._

import akka.actor.ActorSystem
import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }
import akka.stream.ActorMaterializer
import io.akka.grpc.{ Echo, EchoClient, EchoMessage }
import io.grpc.netty.shaded.io.grpc.netty.{ GrpcSslContexts, NegotiationType, NettyChannelBuilder }
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext

import scala.concurrent.Future
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

class HardcodedServiceDiscovery extends SimpleServiceDiscovery {
  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[SimpleServiceDiscovery.Resolved] =
    Future.successful(
      Resolved(name, ResolvedTarget("127.0.0.1", Some(8443)) :: Nil
      )
    )

}


private class PlaceHolder

private object ChannelBuilderUtils {

  def build(host: String, port: Int) = {

    val sslContext: SslContext =
      try
        GrpcSslContexts.forClient.trustManager(loadCert("ca.pem")).build
      catch {
        case ex: Exception => throw new RuntimeException(ex)
      }

    val builder =
      NettyChannelBuilder
        .forAddress(host, port)
        .flowControlWindow(65 * 1024)
        .negotiationType(NegotiationType.TLS)
        .sslContext(sslContext)

    builder.overrideAuthority("foo.test.google.fr")

    builder.build
  }

  // TODO: remove this duplication (see the server code)
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