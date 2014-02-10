package com.tommo.kademlia

import com.tommo.kademlia.protocol.Host
import com.tommo.kademlia.identity._
import akka.actor.ActorSystem
import akka.actor.Props

class KadAkka(private val actorSystem: ActorSystem = ActorSystem("kadSystem"))(implicit val config: KadConfig) {
  self: KadActorProvider with IdGenerator =>

  protected val kadActor = actorSystem.actorOf(Props(newKadActor(selfId)))

  val selfId = generateId(config.addressSpace)

  init

  protected def init {}

  def put(key: Id, value: Any) {
    require(key.size == selfId.size)
    
    // put the key pair in the kth closest 
  }

  def get(key: Id) {
    require(key.size == selfId.size)

    // return an option containing the value
  }
  
  def shutdown() {
	  // invoke stop and wait until all actors are finished processsing then finally invoke shutdown
  }

}

private class ExistingKadNetwork(network: Host, actorSystem: ActorSystem = ActorSystem("kadSystem"))(implicit val kadConf: KadConfig) extends KadAkka(actorSystem) {
  self: KadActorProvider with IdGenerator =>

  override protected def init {
    kadActor ! "Joining" // send msg
  }
}

object KadAkka {
  def apply(implicit config: KadConfig): KadAkka = new KadAkka with KadActorProvider with IdGenerator
  def apply(network: Host, config: KadConfig): KadAkka = new ExistingKadNetwork(network)(config) with KadActorProvider with IdGenerator
}