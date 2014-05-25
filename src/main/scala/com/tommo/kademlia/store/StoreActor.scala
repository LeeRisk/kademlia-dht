package com.tommo.kademlia.store

import scala.concurrent.duration._
import scala.collection.mutable.Map
import akka.actor.{ Actor, ActorRef }

import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.util.EventSource._
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.util.RefreshActor._
import com.tommo.kademlia.KadConfig
import com.tommo.kademlia.lookup._

class StoreActor[V](selfId: Id, kBucketRef: ActorRef, reqSenderRef: ActorRef, timerRef: ActorRef, lookupRef: ActorRef)(implicit val config: KadConfig) extends Actor {
  this: Store[V] with Clock =>

  import StoreActor._
  import config._
  import context._

  override def preStart() {
    kBucketRef ! RegisterListener(self)
  }

  override def postStop() {
    kBucketRef ! UnregisterListener(self)
  }

  val expireMap = Map[Id, Int]()

  def receive = {
    case insertMsg: Insert[V] =>
      import insertMsg._
      insert(key, value)
      lookupRef ! LookupNode.FindKClosest(key)

    case LookupNode.Result(key, nodes) =>
      get(key) match {
        case Some(toStore) =>
          nodes.map(n => NodeRequest(n.ref, StoreRequest(key, null))).foreach(reqSenderRef ! _)
          timerRef ! Republish(key, republishOriginal)
        case None =>
      }

    case Add(newNode) => // listener event from kbucket that signals a new node was added
      val toReplicate = findCloserThan(selfId, newNode.id).map { case (key, value) => NodeRequest(newNode.ref, StoreRequest(key, null), false) }
      toReplicate.foreach(reqSenderRef ! _)

    case storeReq: StoreRequest[V] =>
      import storeReq._
      insert(key, toStore.value)
      kBucketRef ! GetNumNodesInBetween(key)

    case NumNodesInBetween(key, numNode) =>
      scheduleExpire(key, numNode)

    case RefreshDone(key: Id, ExpireValue(rValue)) if (expireMap.get(key).get == rValue) =>
      expireMap -= key
      remove(key)

    case RefreshDone(key: Id, RepublishValue) =>
      lookupRef ! LookupNode.FindKClosest(key)
  }

  private def scheduleExpire(key: Id, numNode: Int) { // TODO need to consider when future nodes are added and adjust the timer accordingly
    def updateExpireMap() {
      expireMap.get(key) match {
        case Some(count) => expireMap += key -> (count + 1)
        case None => expireMap += key -> 0
      }
    }

    def getExpireTime(expire: Duration) = expire * (1 / scala.math.exp(numNode.toDouble / kBucketSize))

    updateExpireMap()
    timerRef ! ExpireRemoteStore(key, getExpireTime(expireRemote), ExpireValue(expireMap.get(key).get))
  }
}

object StoreActor {
  case class Insert[V](key: Id, value: V)
  case class Get(key: Id)
  case class GetResult[V](value: Option[V])

  case class Republish(val key: Id, val after: Duration, val value: RepublishValue.type = RepublishValue, val refreshKey: String = "republishStore") extends Refresh
  case object RepublishValue

  case class ExpireRemoteStore(val key: Id, val after: Duration, val value: ExpireValue, val refreshKey: String = "expireRemoteStore") extends Refresh
  case class ExpireValue(storeCount: Int)

}
