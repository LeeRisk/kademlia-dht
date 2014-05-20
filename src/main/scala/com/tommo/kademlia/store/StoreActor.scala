package com.tommo.kademlia.store

import scala.concurrent.duration._
import scala.collection.mutable.Map
import akka.actor.{ Actor, ActorRef }

import com.tommo.kademlia.util.EventSource._
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.util.RefreshActor._
import com.tommo.kademlia.KadConfig

class StoreActor[V](id: Id, kBucketRef: ActorRef, reqSenderRef: ActorRef, timerRef: ActorRef)(implicit val config: KadConfig) extends Actor {
  this: Store[V] =>

  import StoreActor._
  import config._
  import context._

  override def preStart() {
    kBucketRef ! RegisterListener(self)
  }

  override def postStop() {
    kBucketRef ! UnregisterListener(self)
  }

  val expireMap = Map[Id, Duration]() // TODO race condition there can multiple store requests with the same id; the previous expire timer can remove the value

  def receive = {
    case insertMsg: Insert[V] =>
      import insertMsg._
      insert(key, value)
      timerRef ! Republish(key, refreshStore)
    case Add(newNode) => // listener event from kbucket that signals a new node was added
      val toReplicate = findCloserThan(id, newNode.id).map { case (key, value) => NodeRequest(newNode.ref, StoreRequest(id, key, value), false) }
      toReplicate.foreach(reqSenderRef ! _)
    case storeReq: StoreRequest[V] =>
      import storeReq._
      insert(key, value)
      kBucketRef ! GetNumNodesInBetween(key)
    case NumNodesInBetween(key, numNode) =>
      scheduleExpire(key, numNode)
    case RefreshDone(key: Id, _: ExpireValue.type) => println("ASDF"); remove(key)
  }

  private def scheduleExpire(key: Id, numNode: Int) {
    def updateExpireMap() {
      expireMap.get(id) match {
        case Some(prevExpire) => expireMap += (key -> getExpireTime(prevExpire))
        case None => expireMap += (key -> getExpireTime(refreshStore))
      }

      def getExpireTime(expire: Duration) = expire * (1 / scala.math.exp(numNode.toDouble / kBucketSize))
    }
    
    updateExpireMap()
    timerRef ! ExpireRemoteStore(key, expireMap.get(key).get)
  }
}

object StoreActor {
  case class Insert[V](key: Id, value: V)

  case class Republish(val key: Id, val after: Duration, val value: RepublishValue.type = RepublishValue, val refreshKey: String = "republishStore") extends Refresh
  case object RepublishValue

  case class ExpireRemoteStore(val key: Id, val after: Duration, val value: ExpireValue.type = ExpireValue, val refreshKey: String = "expireRemoteStore") extends Refresh
  case object ExpireValue

}
