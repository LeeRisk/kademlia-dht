package com.tommo.kademlia

import akka.actor.{ Actor, ActorRef }
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.util.EventSource

import scala.concurrent.duration.FiniteDuration

class LookupActor(kBucketRef: ActorRef, timerRef: ActorRef)(implicit val config: KadConfig) extends Actor with EventSource {

  import config._
  import LookupActor._

  override def preStart() {
    kBucketRef ! GetNumKBuckets
  }
  
  def receive = uninit

  def uninit: Receive = eventSourceReceive orElse {
    case NumKBuckets(bucketCount) => kBucketRef ! GetRandomId((0 until bucketCount).toList)
    case RandomId(randIds) => 
      randIds.foreach(r => timerRef ! RefreshBucketTimer(r._1, r._2, refreshStaleKBucket))
      sendEvent(Ready)
      context.become(init)
  }
  
  def init: Receive = {
    case NumKBuckets(bucketCount) => kBucketRef ! GetRandomId((0 until bucketCount).toList)
    case RandomId(randIds) => randIds.foreach(r => timerRef ! RefreshBucketTimer(r._1, r._2, refreshStaleKBucket))
  }
}

object LookupActor {
  case object Ready
  case class RefreshBucketTimer(key: Int, value: Id, after: FiniteDuration)
}