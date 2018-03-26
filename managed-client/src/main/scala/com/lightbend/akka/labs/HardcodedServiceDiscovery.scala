package com.lightbend.akka.labs

import akka.discovery.SimpleServiceDiscovery
import akka.discovery.SimpleServiceDiscovery.{ Resolved, ResolvedTarget }

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class HardcodedServiceDiscovery extends SimpleServiceDiscovery {
  override def lookup(name: String, resolveTimeout: FiniteDuration): Future[SimpleServiceDiscovery.Resolved] =
    Future.successful(
      Resolved(name, ResolvedTarget("127.0.0.1", Some(8443)) :: Nil
      )
    )

}
