package com.tommo.kademlia

import com.tommo.kademlia.protocol.{ Host, Node }
import com.tommo.kademlia.identity._
import com.tommo.kademlia.store.StoreActor.Insert
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.lookup.LookupValueFSM.{ FindValue, Result }
import akka.actor.{ ActorSystem, ActorSelection, ActorRef }
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.Future
import akka.actor.Props
import Kademlia._

class Kademlia[V](val selfId: Id)(implicit val config: KadConfig) {
  self: KadActorProvider[V] =>

  private[kademlia] val actorSystem: ActorSystem = ActorSystem(config.name)

  private[kademlia] val selfNode: ActorRef = actorSystem.actorOf(Props(newNode(selfId)), nodeName)

  implicit val askTimeOut: Timeout = config.requestTimeOut

  def put(key: Id, value: V) = {
    require(key.size == selfId.size)
    selfNode ! Insert(key, value)
  }

  def get(key: Id): Future[Option[V]] = {
    require(key.size == selfId.size)

    import actorSystem.dispatcher

    selfNode.ask(FindValue(key)).mapTo[Result[Either[List[ActorNode], V]]].map {
      case Result(Left(_)) => None
      case Result(Right(v)) => Some(v.asInstanceOf[V])
    }
  }

  def terminate() {
    actorSystem.shutdown()
  }
}

private[kademlia] class ExistingKademlia[V](existing: ExistingHost, selfId: Id)(implicit val kadConf: KadConfig) extends Kademlia[V](selfId) {
  self: KadActorProvider[V] with RemoteSelector =>

  import actorSystem.dispatcher
  import scala.util.{ Success, Failure }
  getSelection(existing, actorSystem).resolveOne(kadConf.requestTimeOut).onComplete {
    case Success(ref) => selfNode ! ref
    case Failure(ex) => throw new RuntimeException(ex)
  }
}

object Kademlia {
  trait RemoteSelector {
    def getSelection(remote: ExistingHost, sys: ActorSystem): ActorSelection = sys.actorSelection(getPath(remote))
  }

  private[kademlia] def getPath(existing: ExistingHost) = "akka.udp://" + existing.name + "@" + existing.host.hostname + ":" + existing.host.port + "/user/" + nodeName

  val nodeName = "kadNode"

  case class ExistingHost(name: String, host: Host)

  def apply[V](idSize: Int, generator: IdGenerator)(implicit config: KadConfig): Kademlia[V] = Kademlia(generator.generateId(idSize))
  def apply[V](id: Id)(implicit config: KadConfig) = new Kademlia[V](id) with KadActorProvider[V]

  def apply[V](idSize: Int, generator: IdGenerator, remote: ExistingHost)(implicit config: KadConfig): Kademlia[V] = Kademlia(generator.generateId(idSize), remote)
  def apply[V](id: Id, remote: ExistingHost)(implicit config: KadConfig) = new ExistingKademlia[V](remote, id) with ExistingKadActor[V] with RemoteSelector
}