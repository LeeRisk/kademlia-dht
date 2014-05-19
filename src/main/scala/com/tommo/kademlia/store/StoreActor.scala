package com.tommo.kademlia.store

import akka.actor.{ Actor, ActorRef }
import com.tommo.kademlia.util.EventSource._
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.protocol.Message._
import scala.concurrent.duration.FiniteDuration
import com.tommo.kademlia.util.RefreshActor._

class StoreActor[V](val selfId: Id, val republish: FiniteDuration, kBucketRef: ActorRef, reqSenderRef: ActorRef, timerRef: ActorRef) extends Actor {
  this: Store[V] =>

  import StoreActor._

  override def preStart() {
    kBucketRef ! RegisterListener(self)
  }

  override def postStop() {
    kBucketRef ! UnregisterListener(self)
  }

  def receive = {
    case msg: Insert[V] => 
      insert(msg.key, msg.value)
      timerRef ! RefreshOriginal(msg.key, republish)
    case Add(newNode) => // listener event from kbucket that signals a new node was added
      val toReplicate = findCloserThan(selfId, newNode.id).map { case (id, value) => NodeRequest(newNode.ref, StoreRequest(selfId, id, value), false) }
      toReplicate.foreach(reqSenderRef ! _)
    case _ =>
  }
}

object StoreActor {
  case class Insert[V](key: Id, value: V)
  case class RefreshOriginal(val key: Id, val after: FiniteDuration, val value: Any = None, val refreshKey: String = "refreshOriginalPublisher") extends Refresh

}

// default expiration timer * 1/e^(n/k) 