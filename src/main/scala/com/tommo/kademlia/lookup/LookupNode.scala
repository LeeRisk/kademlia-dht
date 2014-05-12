package com.tommo.kademlia.lookup

import scala.concurrent.duration.FiniteDuration

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.Message.{ KClosestReply, KClosestRequest }
import LookupFSM.{ QueryNodeData, Lookup }

import akka.actor.ActorRef

class LookupNode(selfId: Id, kBucketActor: ActorRef, reqSender: ActorRef, kBucketSize: Int, alpha: Int, roundTimeOut: FiniteDuration)
  extends LookupFSM(selfId, kBucketActor, reqSender, kBucketSize, alpha, roundTimeOut) {

  import LookupNode._

  def localKClosestReq = { case Event(reply: KClosestReply, req: Lookup) => localKclosestState(structuralReply(reply), req) }

  def getRequest(lookupId: Id, k: Int) = KClosestRequest(selfId, lookupId, k)

  def remoteReplySF = {
    case Event(reply: KClosestReply, qd: QueryNodeData) => kclosestState(structuralReply(reply), qd)
  }
}

object LookupNode {
  def structuralReply(reply: KClosestReply) = new {
    val nodes = reply.nodes
    val senderId = reply.sender
  }
}