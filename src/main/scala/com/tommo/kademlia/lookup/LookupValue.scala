package com.tommo.kademlia.lookup

import scala.concurrent.duration.FiniteDuration

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.Message.{ FindValueReply, FindValueRequest, CacheStoreRequest }
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.store.StoreActor._
import LookupNode._
import LookupValue._

import akka.actor.ActorRef

class LookupValue[V](selfNode: ActorNode, storeRef: ActorRef, kBucketRef: ActorRef, reqSender: ActorRef, kBucketSize: Int, alpha: Int, roundTimeOut: FiniteDuration)
  extends LookupNode(selfNode, kBucketRef, reqSender, kBucketSize, alpha, roundTimeOut) {

  override def remoteKClosest(lookupId: Id, k: Int) = FindValueRequest(lookupId, k)

  when(Initial){
    case Event(req @ FindValue(searchId), _) =>
      storeRef ! Get(searchId)
      stay using Lookup(searchId, sender)
    case Event(res: GetResult[V], req: Lookup) => 
      res.value match {
        case Some(v) => goto(FinalizeValue) using FinalizeValueData(req, v)
        case None => 
          self.tell(FindKClosest(req.id), req.sender)
          stay using req
      }
  } orElse queryKbucketSF

  override def remoteReplySF = {
    case Event(reply: FindValueReply[V], qd: QueryNodeData) =>
      reply.result match {
        case Left(kclosest) => kclosestState(kclosest, qd)
        case Right(remoteValue) =>
          val toSendRef = qd.seen.filter(Function.tupled((id, node) => node.respond)).headOption

          toSendRef match {
            case Some((id, nq)) => reqSender ! NodeRequest(nq.ref, CacheStoreRequest(qd.req.id, remoteValue))
            case _ =>
          }

          goto(FinalizeValue) using FinalizeValueData(qd.req, remoteValue.value)
      }
  }

  override def returnResultsAs(searchId: Id, kclosest: List[ActorNode]) = LookupValue.Result(Left(kclosest))

  when(FinalizeValue) {
    case Event(Start, FinalizeValueData(req, value)) =>
      req.sender ! LookupValue.Result(Right(value))
      stop()
  }

  onTransition {
    case QueryNode -> FinalizeValue => initStateTimer("startFinalizeValue")
  }
}

object LookupValue {
  case class FindValue(searchId: Id) 
  case class Result[V](result: Either[List[ActorNode], V])

  case object FinalizeValue extends State
  case class FinalizeValueData[V](req: Lookup, value: V) extends Data
}