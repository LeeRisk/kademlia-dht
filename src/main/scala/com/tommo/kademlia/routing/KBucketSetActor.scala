package com.tommo.kademlia.routing

import com.tommo.kademlia.KadConfig
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.util.EventSource
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.protocol.RequestSenderActor._
import akka.pattern.{ ask, pipe, AskTimeoutException }
import akka.actor.Actor
import akka.actor.ActorRef
import akka.event.Logging

class KBucketSetActor(id: Id, requestSender: ActorRef)(implicit kadConfig: KadConfig) extends Actor with EventSource {
  this: KBucketSet.Provider =>

  import KBucketSetActor._
  import context._
  import kadConfig._
  
  val kSet = newKSet[ActorNode](id, kBucketSize) // TODO don't want to restart this actor if exception occurs since it contains routing info

  def receive = eventSourceReceive orElse {
    case GetNumNodesInBetween(id) => sender ! NumNodesInBetween(kSet.getNodesBetween(id))
    case GetRandomIdInSameBucketAs(id) => 
      val index = kSet.getKBucketIndex(id)
      sender ! RandomId((index, kSet.getRandomId(index)) :: Nil)
    case GetRandomId(buckets) => sender ! RandomId(buckets.map(b => (b, kSet.getRandomId(b))))
    case GetNumKBuckets => sender ! NumKBuckets(kSet.addressSize)
    case KClosestRequest(searchId, k) => sender ! KClosestReply(id, kSet.getClosestInOrder(k, searchId))
    case addReq @ Add(node) if node.id != id =>
      doAdd(node)
      sendEvent(addReq)
    case RequestTimeout(PingRequest, Some((dead: ActorNode, toAdd: ActorNode))) if (kSet.contains(dead)) =>
      kSet.remove(dead)
      kSet.add(toAdd)
  }

  private def doAdd(toAdd: ActorNode) {
    if (!kSet.isFull(toAdd)) {
      kSet.add(toAdd)
    } else {
      val lowestOrder = kSet.getLowestOrder(toAdd)
      requestSender ! NodeRequest(lowestOrder.ref, PingRequest, customData = (lowestOrder, toAdd))
    }
  }
}

object KBucketSetActor {
  case class Add(node: ActorNode)

  case object GetNumKBuckets
  case class NumKBuckets(numBuckets: Int)
  
  case class GetRandomId(buckets: List[Int])
  case class GetRandomIdInSameBucketAs(id: Id)
  case class RandomId(randIds: List[(Int, Id)]) // list of tuple that represents (index of bucketset, id)
  
  case class GetNumNodesInBetween(id: Id)
  case class NumNodesInBetween(numNode: Int)
}
