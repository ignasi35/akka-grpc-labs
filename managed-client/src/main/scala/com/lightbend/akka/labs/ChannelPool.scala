package com.lightbend.akka.labs

import akka.actor.{ Actor, ActorRef, ActorSystem, Props, Stash }
import akka.discovery.SimpleServiceDiscovery
import akka.pattern.ask
import akka.stream.{ ActorMaterializer, Materializer }
import akka.util.Timeout
import com.lighbend.akka.labs.tools.ChannelBuilderUtils
import com.lightbend.akka.labs.Pool.{ ChannelFactory, GetChannel }
import com.lightbend.akka.labs.utils.HardcodedServiceDiscovery
import io.akka.grpc.{ Echo, EchoClient, EchoMessage }
import io.grpc.{ Channel, ManagedChannel }

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }

/**
  * Given a ServiceDiscovery, builds a channelFactory that uses a pool of
  * channels.
  */
object ChannelPool extends App {

  implicit val system = ActorSystem()
  implicit val ctx = system.dispatcher
  implicit val mat = ActorMaterializer()

  // using a hardcoded ServiceDiscovery to ease testing
  private val discovery: SimpleServiceDiscovery = new HardcodedServiceDiscovery

  val channelFactory: Pool.ChannelFactory = (serviceName: String) => {
    discovery.lookup(serviceName, 500.millis)
      .map {
        case resolved if resolved.addresses.nonEmpty =>
          val address = resolved.addresses.head
          ChannelBuilderUtils.build(address.host, address.port.getOrElse(8443))
        case r => throw new RuntimeException(s"No address available for service $serviceName")
      }
  }

  val client: Echo = new EchoChannelPoolClient(channelFactory)

  private val seq: Seq[Future[Unit]] = (0 to 9999).map { id =>
    client
      .echo(EchoMessage(s"The payload for $id"))
      .map(println)
  }
  Future.sequence(seq)
    .recover { case t => t.printStackTrace() }
    .foreach { _ => System.exit(0) }

}

object Pool {
  type ChannelFactory = (String) => Future[ManagedChannel]

  def props(channelFactory: ChannelFactory)(implicit ex: ExecutionContext) = Props(new Pool(channelFactory))

  case class GetChannel(serviceName: String)

  case class PooledChannel(channel: Channel)

  case class ReturnChannel(channel: Channel)

}

class Pool(channelFactory: Pool.ChannelFactory)(implicit ex: ExecutionContext) extends Actor with Stash {

  import Pool._
  import scala.concurrent.duration._
  import akka.pattern.pipe

  var channelPool: Map[String, ManagedChannel] = Map.empty
  var resolving: Set[String] = Set.empty
  implicit val timeout = Timeout(5 seconds) // needed for `?` below

  override def receive: Receive = {
    // TODO: handle ReturnChannel messages
    case GetChannel(serviceName) =>
      channelPool.get(serviceName) match {
        case Some(chann) => sender() ! Pool.PooledChannel(chann)
        case None if !resolving.contains(serviceName) => {
          resolving += serviceName
          channelFactory(serviceName)
            .mapTo[ManagedChannel]
            .map { ch =>
              channelPool += (serviceName -> ch)
              unstashAll()
              Pool.PooledChannel(ch)
            }
            .transform {
              x =>
                resolving -= serviceName
                x
            }
            .pipeTo(sender)
        }
        case _ => {
          stash()
        }
      }
  }
}

class EchoChannelPoolClient(channelFactory: ChannelFactory)(implicit sys: ActorSystem, mat: Materializer, ctx: ExecutionContext) extends Echo {

  import Pool._

  override def echo(in: EchoMessage): Future[EchoMessage] = withChannel(Echo.name) { ch =>
    EchoClient(ch).echo(in)
  }

  private val pool: ActorRef = sys.actorOf(Pool.props(channelFactory))
  implicit val timeout: Timeout = Timeout(5.seconds) // needed for `?` below

  private def withChannel[T](serviceName: String)(block: (Channel) => Future[T]): Future[T] = {
    (pool ? GetChannel(serviceName)).mapTo[PooledChannel]
      .flatMap { ch =>
        block(ch.channel)
          .transform {
            x => {
              pool ! ReturnChannel(ch.channel)
              x
            }
          }
      }
  }

}
