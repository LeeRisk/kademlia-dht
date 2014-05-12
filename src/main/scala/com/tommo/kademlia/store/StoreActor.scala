package com.tommo.kademlia.store

import akka.actor.{ Actor, ActorRef }
import com.tommo.kademlia.util.EventSource._
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.protocol.Message._

class StoreActor[V](val selfId: Id, kBucketRef: ActorRef, reqSenderRef: ActorRef) extends Actor {
  this: Store[V] =>

  import StoreActor._
  
  override def preStart() {
    kBucketRef ! RegisterListener(self)
  }

  override def postStop() {
    kBucketRef ! UnregisterListener(self)
  }
  
  def receive = {
    case msg: Insert[V] => insert(msg.key, msg.value) 
    case Add(newNode) => 
      val toReplicate = getCloserThan(selfId, newNode.id).map{case (id, values) => values.map(value => NodeRequest(newNode.ref, StoreRequest(selfId, id, value), false))}.flatten
      toReplicate.foreach(reqSenderRef ! _)
    case _ =>
  }
}

object StoreActor {
  case class Insert[V](key: Id, value: V)

}