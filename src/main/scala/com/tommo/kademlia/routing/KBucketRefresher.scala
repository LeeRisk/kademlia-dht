package com.tommo.kademlia.routing

import scala.concurrent.duration.FiniteDuration
import akka.actor.{Actor, ActorRef}

import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.util.EventSource._
import com.tommo.kademlia.KadConfig
import com.tommo.kademlia.util.RefreshActor._
import com.tommo.kademlia.lookup.LookupNodeFSM.FindKClosest
import com.tommo.kademlia.lookup.LookupDispatcher.Lookup
import KBucketRefresher._

class KBucketRefresher(nodeDispatcher: ActorRef, valueDispatcher: ActorRef, kBucketRef: ActorRef, refreshRef: ActorRef)(implicit val config: KadConfig) extends Actor {
  import config._

  override def preStart() {
    nodeDispatcher ! RegisterListener(self)
    valueDispatcher ! RegisterListener(self)

    kBucketRef ! GetRandomId((0 until kBucketSize).toList)
  }

  override def postStop() {
    nodeDispatcher ! UnregisterListener(self)
    valueDispatcher ! UnregisterListener(self)
  }

  def receive = {
    case RandomId(randIds) => randIds.foreach(r => refreshRef ! RefreshBucketTimer(r._1, r._2, refreshStaleKBucket))
    case RefreshDone(_, id: Id) => 
      nodeDispatcher ! FindKClosest(id)
    case Lookup(id) => kBucketRef ! GetRandomIdInSameBucketAs(id)
  }
}

object KBucketRefresher {
  trait Provider {
    def newKBucketRefresher(nodeDispatcher: ActorRef, valueDispatcher: ActorRef, kBucketRef: ActorRef, refreshRef: ActorRef)(implicit config: KadConfig): Actor = 
      new KBucketRefresher(nodeDispatcher, valueDispatcher, kBucketRef, refreshRef)
  }
  
  
  case class RefreshBucketTimer(val key: Int, val value: Id, val after: FiniteDuration, val refreshKey: String = "refreshBucket") extends Refresh
}