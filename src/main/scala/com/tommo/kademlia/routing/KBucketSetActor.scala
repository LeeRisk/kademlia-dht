package com.tommo.kademlia.routing

import com.tommo.kademlia.KadConfig
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.protocol.RequestSenderActor._
import akka.pattern.{ ask, pipe, AskTimeoutException }
import akka.actor.Actor
import akka.actor.ActorRef
import akka.event.Logging

class KBucketSetActor(kSet: KBucketSet[ActorNode], requestSender: ActorRef)(implicit kadConfig: KadConfig) extends Actor {
  import KBucketSetActor._
  import context._
  
  implicit val timeout: akka.util.Timeout = kadConfig.roundTimeOut

  def receive = {
    case GetKClosest(id, k) => sender ! KClosest(id, kSet.getClosestInOrder(k, id))
    case Add(node) if !kSet.isFull(node) => kSet.add(node)
    case Add(node) if kSet.isFull(node) =>
      val lowestOrder = kSet.getLowestOrder(node)
      requestSender ? NodeRequest(lowestOrder.ref, PingRequest(lowestOrder.id)) onFailure {
        case s => self ! AddPingFailure(node, lowestOrder) 
      }
    case AddPingFailure(toAdd, deadNode) if(kSet.contains(deadNode)) =>
      kSet.remove(deadNode)
      kSet.add(toAdd)
  }
}

object KBucketSetActor {
  case class Add(node: ActorNode)
  case class GetKClosest(id: Id, k: Int)
  case class KClosest(id: Id, nodes: List[ActorNode])
  
  private case class AddPingFailure(toAdd: ActorNode, deadNode: ActorNode)
}

