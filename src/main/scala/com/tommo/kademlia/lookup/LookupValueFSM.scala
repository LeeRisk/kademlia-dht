package com.tommo.kademlia.lookup

import scala.concurrent.duration.FiniteDuration
import akka.actor.Actor

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.Message.{ FindValueReply, FindValueRequest, CacheStoreRequest }
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.RequestDispatcher._
import com.tommo.kademlia.store.StoreActor._
import LookupNodeFSM._
import LookupValueFSM._

import akka.actor.ActorRef

class LookupValueFSM[V](selfNode: ActorNode, kBucketRef: ActorRef, storeRef: ActorRef, kBucketSize: Int, alpha: Int, roundTimeOut: FiniteDuration)
  extends LookupNodeFSM(selfNode, kBucketRef, kBucketSize, alpha, roundTimeOut) {

  override def remoteKClosest(lookupId: Id, k: Int) = FindValueRequest(lookupId, k)

  when(Initial) {
    case Event(req @ FindValue(searchId), _) =>
      storeRef ! Get(searchId)
      stay using Lookup(searchId, sender)
    case Event(res: GetResult[V], req: Lookup) =>
      res.value match {
        case Some(v) => goto(FinalizeValue) using FinalizeValueData(req, v._1)
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
            case Some((id, nq)) => selfNode.ref ! NodeRequest(nq.ref, CacheStoreRequest(qd.req.id, remoteValue))
            case _ =>
          }

          goto(FinalizeValue) using FinalizeValueData(qd.req, remoteValue.value)
      }
  }

  override def returnResultsAs(searchId: Id, kclosest: List[ActorNode]) = LookupValueFSM Result (Left(kclosest))

  when(FinalizeValue)({
    case Event(Start, FinalizeValueData(req, value)) =>
      req.sender ! LookupValueFSM.Result(Right(value))
      stop()
  })

  onTransition {
    case QueryNode -> FinalizeValue => initStateTimer("startFinalizeValue")
  }
}

object LookupValueFSM {
  trait Provider {
    def newLookupValueFSM(selfNode: ActorNode, kBucketSetRef: ActorRef, storeRef: ActorRef, kBucketSize: Int, alpha: Int, roundTimeOut: FiniteDuration): Actor =
      new LookupValueFSM(selfNode, kBucketSetRef, storeRef, kBucketSize, alpha, roundTimeOut)
  }

  case class FindValue(searchId: Id)
  case class Result[V](result: Either[List[ActorNode], V])

  case object FinalizeValue extends State
  case class FinalizeValueData[V](req: Lookup, value: V) extends Data
}