package com.tommo.kademlia

import com.tommo.kademlia.protocol.Host
import com.tommo.kademlia.identity._
import akka.actor.ActorSystem
import akka.actor.Props
import Kademlia._

class Kademlia private[kademlia] (val selfId: Id)(implicit val config: KadConfig) {
  self: KadActorProvider =>

  private[kademlia] final val actorSystem: ActorSystem = ActorSystem(config.name)

  protected val selfNode = actorSystem.actorOf(Props(newNetwork(selfId)), nodeName)

  def put(key: Id, value: Any) {
    require(key.size == selfId.size)

    // put the key pair in the kth closest 
  }

  def get(key: Id) {
    require(key.size == selfId.size)

    // return an option containing the value
  }

  def shutdown() {
    actorSystem.shutdown()
  }
}

private class ExistingKadNetwork(existing: ExistingHost, selfId: Id)(implicit val kadConf: KadConfig) extends Kademlia(selfId) {
  self: KadActorProvider =>
  	override protected val selfNode = actorSystem.actorOf(Props(joinNetwork(selfId, existing)), Kademlia.nodeName)
}

object Kademlia {
  val nodeName = "kadNode"

  case class ExistingHost(name: String, host: Host)

  def apply(idSize: Int, generator: IdGenerator)(implicit config: KadConfig): Kademlia = Kademlia(generator.generateId(idSize))
  def apply(id: Id)(implicit config: KadConfig) = new Kademlia(id) with KadActorProvider

  def apply(idSize: Int, generator: IdGenerator, existing: ExistingHost)(implicit config: KadConfig): Kademlia = Kademlia(generator.generateId(idSize), existing)
  def apply(id: Id, existing: ExistingHost)(implicit config: KadConfig): Kademlia = new ExistingKadNetwork(existing, id) with KadActorProvider
}