package com.tommo.kademlia.lookup

import scala.concurrent.duration.FiniteDuration
import akka.actor.{ Actor, ActorRef, Props }

import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.util.EventSource
import com.tommo.kademlia.KadConfig
import com.tommo.kademlia.util.RefreshActor._
import LookupDispatcher._

class LookupDispatcher(kBucketRef: ActorRef, timerRef: ActorRef)(implicit val config: KadConfig) extends Actor {
  self: Provider =>

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

  def lookup(id: Id, lookupFn: () => ActorRef = lookupNode) {
    lookupFn() forward id
    kBucketRef ! GetRandomIdInSameBucketAs(id)
 }
}

object LookupDispatcher {
  case class RefreshBucketTimer(val key: Int, val value: Id, val after: FiniteDuration, val refreshKey: String = "refreshBucket") extends Refresh

  trait Provider {
    this: Actor =>
    def lookupNode(): ActorRef
    def lookupValue(): ActorRef
  }
}