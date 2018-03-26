package com.lighbend.akka.labs.tools

import java.io.File

import io.grpc.netty.shaded.io.grpc.netty.{ GrpcSslContexts, NegotiationType, NettyChannelBuilder }
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext


object ChannelBuilderUtils {

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

  def loadCert(name: String): File = TestUtils.loadCert(name)
}
