import akka.grpc.gen.scaladsl.ScalaClientCodeGenerator

organization := "com.lightbend.akka.grpc"

// root
lazy val `akka-grpc-labs-root` = project
  .in(file("."))
  .aggregate(
    `akka-grpc-labs-server`,
    `akka-grpc-labs-client`)



lazy val `akka-grpc-labs-client` = project.in(file("client"))
  .enablePlugins(JavaAgent, JavaAgent, AkkaGrpcPlugin)
  .settings(
    name := "akka-grpc-labs-client",
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime",
    PB.protoSources in Compile += target.value / "protobuf",
    (akkaGrpcCodeGenerators in Compile) := Seq(
      GeneratorAndSettings(ScalaClientCodeGenerator, (akkaGrpcCodeGeneratorSettings in Compile).value))
  )

lazy val `akka-grpc-labs-server` = project.in(file("server"))
  .enablePlugins(JavaAgent, JavaAgent, AkkaGrpcPlugin)
  .settings(
    name := "akka-grpc-labs-client",
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime",
    PB.protoSources in Compile += target.value / "protobuf"
  )
