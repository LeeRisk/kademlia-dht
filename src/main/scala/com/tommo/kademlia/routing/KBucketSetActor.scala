package com.tommo.kademlia.routing

import akka.actor.Actor

import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.ActorNode

class KBucketSetActor(kSet: KBucketSet[ActorNode]) extends Actor {
  import KBucketSetActor._
  
  
  def receive = {
    case GetKClosest(id, k) => sender ! KClosest(id, kSet.getClosestInOrder(k, id))
  }
}

object KBucketSetActor {
  case class Add(node: ActorNode)
  case class GetKClosest(id: Id, k: Int)
  case class KClosest(id: Id, nodes: List[ActorNode])
}