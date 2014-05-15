package com.tommo.kademlia.lookup

import scala.concurrent.duration.FiniteDuration
import akka.actor.{ Actor, ActorRef, Props }

import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.util.EventSource
import com.tommo.kademlia.KadConfig
import com.tommo.kademlia.util.RefreshActor._
import LookupActor._

class LookupActor(kBucketRef: ActorRef, timerRef: ActorRef)(implicit val config: KadConfig) extends Actor with EventSource {
  self: Provider =>

  import config._

  override def preStart() {
    kBucketRef ! GetNumKBuckets
  }

  def receive = uninit

  def uninit: Receive = eventSourceReceive orElse {
    case NumKBuckets(bucketCount) => kBucketRef ! GetRandomId((0 until bucketCount).toList)
    case RandomId(randIds) =>
      randIds.foreach(r => timerRef ! RefreshBucketTimer(r._1, RefreshBucket(r._2), refreshStaleKBucket))
      sendEvent(Ready)
      context.become(init)
  }

  def init: Receive = {
    case RandomId(randIds) => randIds.foreach(r => timerRef ! RefreshBucketTimer(r._1, RefreshBucket(r._2), refreshStaleKBucket))
    case RefreshBucket(id) =>
      lookup(id)
    case FindKNode(id: Id) =>
      lookup(id)
    case FindKValue(id: Id) =>
      lookup(id, lookupValue)
  }

  def lookup(id: Id, lookupFn: () => ActorRef = lookupNode) {
    lookupFn() forward id
    kBucketRef ! GetRandomIdInSameBucketAs(id)
  }
}

object LookupActor {
  case class FindKNode(id: Id)
  case class FindKValue(id: Id)

  case object Ready

  case class RefreshBucketTimer(val key: Int, val event: RefreshBucket, val after: FiniteDuration, val refreshKey: String = "refreshBucket") extends Refresh
  case class RefreshBucket(value: Id)

  trait Provider {
    this: Actor =>
    def lookupNode(): ActorRef
    def lookupValue(): ActorRef
  }
}