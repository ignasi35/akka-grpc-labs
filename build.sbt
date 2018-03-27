import akka.grpc.gen.scaladsl.ScalaClientCodeGenerator

organization := "com.lightbend.akka.grpc"

// root
lazy val `akka-grpc-labs-root` = project
  .in(file("."))
  .aggregate(
    `tools`,
    `akka-grpc-server`,
    `akka-grpc-basic-client`,
    `akka-grpc-managed-client`,
  )


lazy val `tools` = project.in(file("tools"))
  .enablePlugins(AkkaGrpcPlugin)
  .settings(
    name := "tools",
  )

lazy val `akka-grpc-basic-client` = project.in(file("basic-client"))
  .enablePlugins(JavaAgent, AkkaGrpcPlugin)
  .settings(
    name := "akka-grpc-basic-client",
    PB.protoSources in Compile += target.value / "protobuf",
    (akkaGrpcCodeGenerators in Compile) := Seq(
      GeneratorAndSettings(ScalaClientCodeGenerator, (akkaGrpcCodeGeneratorSettings in Compile).value)),
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime",
  )
  .dependsOn(`tools`)

lazy val `akka-grpc-managed-client` = project.in(file("managed-client"))
  .enablePlugins(JavaAgent, AkkaGrpcPlugin)
  .settings(
    name := "akka-grpc-managed-client",
    PB.protoSources in Compile += target.value / "protobuf",
    (akkaGrpcCodeGenerators in Compile) := Seq(
      GeneratorAndSettings(ScalaClientCodeGenerator, (akkaGrpcCodeGeneratorSettings in Compile).value)),
    libraryDependencies += "com.lightbend.akka.discovery" %% "akka-discovery-dns" % "0.10.0",
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime",
  )
  .dependsOn(`tools`)

lazy val `akka-grpc-server` = project.in(file("server"))
  .enablePlugins(JavaAgent, AkkaGrpcPlugin)
  .settings(
    name := "akka-grpc-server",
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.6" % "runtime",
    PB.protoSources in Compile += target.value / "protobuf"
  )
  .dependsOn(`tools`)
