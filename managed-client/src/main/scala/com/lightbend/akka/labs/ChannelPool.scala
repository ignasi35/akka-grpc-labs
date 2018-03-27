package com.lightbend.akka.labs

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
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

  val channelFactory: () => Future[ManagedChannel] = () => {
    discovery.lookup(Echo.name, 500.millis)
      .map {
        case resolved if resolved.addresses.nonEmpty =>
          val address = resolved.addresses.head
          ChannelBuilderUtils.build(address.host, address.port.getOrElse(8443))
        case r => throw new RuntimeException(s"No address available for service ${r.serviceName}")
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
  type ChannelFactory = () => Future[ManagedChannel]

  def props(channelFactory: ChannelFactory) = Props(new Pool(channelFactory))

  case object GetChannel

  case class PooledChannel(channel: Channel)

  case class ReturnChannel(channel: Channel)

}

class Pool(channelFactory: ChannelFactory) extends Actor {

  var channel: ManagedChannel = null


  override def preStart(): Unit = {
    channel = Await.result(channelFactory(), 5.seconds)
  }

  override def receive: Receive = {
    case _: GetChannel.type => sender() ! Pool.PooledChannel(channel)
  }

}

class EchoChannelPoolClient(channelFactory: ChannelFactory)(implicit sys: ActorSystem, mat: Materializer, ctx: ExecutionContext) extends Echo {

  import Pool._

  override def echo(in: EchoMessage): Future[EchoMessage] = withChannel { ch =>
    EchoClient(ch).echo(in)
  }

  private val pool: ActorRef = sys.actorOf(Pool.props(channelFactory))
  implicit val timeout = Timeout(5 seconds) // needed for `?` below

  private def withChannel[T](block: (Channel) => Future[T]): Future[T] = {
    (pool ? GetChannel).mapTo[PooledChannel]
      .flatMap { ch =>
        block(ch.channel)
          .transform {
            x => {
              pool ! ReturnChannel
              x
            }
          }
      }
  }

}
