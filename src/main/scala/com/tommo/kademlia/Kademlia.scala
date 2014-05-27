package com.tommo.kademlia

import com.tommo.kademlia.protocol.{ Host, Node }
import com.tommo.kademlia.identity._
import akka.actor.ActorSystem
import scala.concurrent.Future
import akka.actor.Props
import Kademlia._

class Kademlia[V] private[kademlia] (val selfId: Id)(implicit val config: KadConfig) {
  self: KadActorProvider[V] =>

  private[kademlia] final val actorSystem: ActorSystem = ActorSystem(config.name)

  protected val selfNode = actorSystem.actorOf(Props(newNetwork(selfId)), nodeName)

  def put(key: Id, value: V): Future[List[Node]] = {
    require(key.size == selfId.size)
    return null
  }

  def get(key: Id): Future[Option[V]] = {
    require(key.size == selfId.size)
    return null
  }

  def shutdown() {
    actorSystem.shutdown()
  }
}

private[kademlia] class ExistingKadNetwork[V](existing: ExistingHost, selfId: Id)(implicit val kadConf: KadConfig) extends Kademlia[V](selfId) {
  self: KadActorProvider[V] =>
  	override protected val selfNode = actorSystem.actorOf(Props(joinNetwork(selfId, existing)), Kademlia.nodeName)
}

object Kademlia {
  val nodeName = "kadNode"

  case class ExistingHost(name: String, host: Host)

  def apply[V](idSize: Int, generator: IdGenerator)(implicit config: KadConfig): Kademlia[V] = Kademlia(generator.generateId(idSize))
  def apply[V](id: Id)(implicit config: KadConfig) = new Kademlia[V](id) with KadActorProvider[V]

  def apply[V](idSize: Int, generator: IdGenerator, existing: ExistingHost)(implicit config: KadConfig): Kademlia[V] = Kademlia(generator.generateId(idSize), existing)
  def apply[V](id: Id, existing: ExistingHost)(implicit config: KadConfig) = new ExistingKadNetwork[V](existing, id) with KadActorProvider[V]
}