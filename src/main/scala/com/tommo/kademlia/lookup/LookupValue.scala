package com.tommo.kademlia.lookup

import scala.concurrent.duration.FiniteDuration

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.Message.{ FindValueReply, FindValueRequest, StoreRequest }
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.RequestSenderActor._
import LookupFSM._
import LookupValue._

import akka.actor.ActorRef

class LookupValue[V](selfId: Id, storeRef: ActorRef, reqSender: ActorRef, kBucketSize: Int, alpha: Int, roundTimeOut: FiniteDuration)
  extends LookupFSM(selfId, storeRef, reqSender, kBucketSize, alpha, roundTimeOut) {

  def getRequest(lookupId: Id, k: Int) = FindValueRequest(selfId, lookupId, k)

  def remoteReplySF = {
    case Event(reply: FindValueReply[V], qd: QueryNodeData) =>
      reply.result match {
        case Left(knodes) =>
          kclosestState(new {
            val nodes = knodes
            val senderId = reply.sender
          }, qd)
        case Right(values) => 
          val toSendRef = qd.seen.filter(Function.tupled((id, node) => node.respond)).headOption
          
          toSendRef match {
            case Some((id, nq)) => reqSender ! NodeRequest(nq.ref, StoreRequest(selfId, qd.req.id, values))
            case _ =>
          }
          
          goto(FinalizeValue) using ResultValue(qd.req, values)
      }
  }

  def localKClosestReq = {
    case Event(FindValueReply(_, result), req: Lookup) =>
      result match {
        case Left(kclosest) => localKclosestState(new {
          val nodes = kclosest
        }, req)
        case Right(values) => goto(FinalizeValue) using ResultValue(req, values)
      }
  }
  
  override def returnResultsAs(kclosest: List[ActorNode]) = Left(kclosest)

  when(FinalizeValue) {
    case Event(Start, ResultValue(req, values)) =>
      req.sender ! Right(values)
      stop()
  }

  onTransition {
    case QueryNode -> FinalizeValue => initStateTimer("startFinalizeValue")
  }
}

object LookupValue {
  case object FinalizeValue extends State
  case class ResultValue[V](req: Lookup, values: Set[V]) extends Data
}