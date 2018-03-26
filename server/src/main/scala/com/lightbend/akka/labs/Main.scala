package com.lightbend.akka.labs

import java.io._
import java.nio.file.{ Files, Paths }
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{ KeyFactory, KeyStore, SecureRandom }
import java.util.Base64
import javax.net.ssl.{ KeyManagerFactory, SSLContext }

import akka.actor.ActorSystem
import akka.http.scaladsl.{ Http2, HttpsConnectionContext }
import akka.stream.ActorMaterializer
import io.akka.grpc.EchoHandler
import io.grpc.netty.shaded.io.grpc.netty.{ GrpcSslContexts, NegotiationType, NettyChannelBuilder }
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext

/**
  *
  */
object Main extends App {

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  val echoHandler = EchoHandler(new EchoImpl)

  Http2().bindAndHandleAsync(
    echoHandler,
    interface = "localhost",
    port = 8443,
    httpsContext = serverHttpContext())

  private def serverHttpContext() = {
    val keyEncoded = new String(Files.readAllBytes(Paths.get(TestUtils.loadCert("server1.key").getAbsolutePath)), "UTF-8")
      .replace("-----BEGIN PRIVATE KEY-----\n", "")
      .replace("-----END PRIVATE KEY-----\n", "")
      .replace("\n", "")

    val decodedKey = Base64.getDecoder.decode(keyEncoded)

    val spec = new PKCS8EncodedKeySpec(decodedKey)

    val kf = KeyFactory.getInstance("RSA")
    val privateKey = kf.generatePrivate(spec)

    val fact = CertificateFactory.getInstance("X.509")
    val is = new FileInputStream(TestUtils.loadCert("server1.pem"))
    val cer = fact.generateCertificate(is)

    val ks = KeyStore.getInstance("PKCS12")
    ks.load(null)
    ks.setKeyEntry("private", privateKey, Array.empty, Array(cer))

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, null)

    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, null, new SecureRandom)

    new HttpsConnectionContext(context)
  }

}


private class PlaceHolder

private object TestUtils {


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