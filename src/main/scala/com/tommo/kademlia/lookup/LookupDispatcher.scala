package com.tommo.kademlia.lookup

import scala.concurrent.duration.FiniteDuration
import akka.actor.{ Actor, ActorRef, Props }

import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.util.EventSource
import com.tommo.kademlia.KadConfig
import com.tommo.kademlia.util.RefreshActor._
import com.tommo.kademlia.protocol.ActorNode
import LookupDispatcher._

class LookupDispatcher(selfNode: ActorNode, reqSender: ActorRef, kBucketRef: ActorRef, timerRef: ActorRef)(implicit val config: KadConfig) extends Actor {
  self: LookupNode.Provider with LookupValue.Provider =>

  import config._

  override def preStart() {
    kBucketRef ! GetNumKBuckets
  }

  def receive = {
    case NumKBuckets(bucketCount) => kBucketRef ! GetRandomId((0 until bucketCount).toList)
    case RandomId(randIds) => randIds.foreach(r => timerRef ! RefreshBucketTimer(r._1, r._2, refreshStaleKBucket))
    case RefreshDone(_, id: Id) => lookup(id)
    case LookupNode.FindKClosest(id: Id) => lookup(id)
    case LookupValue.FindValue(id: Id) => lookup(id, lookupValue)
  }

  def lookupNode() = context.actorOf(Props(newLookupNodeActor(selfNode, kBucketRef, reqSender, kBucketSize, roundConcurrency, roundTimeOut)))
  def lookupValue() = context.actorOf(Props(newLookupValueActor(selfNode, kBucketRef, reqSender, kBucketSize, roundConcurrency, roundTimeOut)))

  def lookup(id: Id, lookupFn: => ActorRef = lookupNode) {
    lookupFn forward id
    kBucketRef ! GetRandomIdInSameBucketAs(id)
  }
}

object LookupDispatcher {
  trait Provider {
    def newLookupDispatcher(selfNode: ActorNode, reqSender: ActorRef, kBucketRef: ActorRef, timerRef: ActorRef)(implicit config: KadConfig): Actor =
      new LookupDispatcher(selfNode, reqSender, kBucketRef, timerRef) with LookupNode.Provider with LookupValue.Provider
  }

  case class RefreshBucketTimer(val key: Int, val value: Id, val after: FiniteDuration, val refreshKey: String = "refreshBucket") extends Refresh

}