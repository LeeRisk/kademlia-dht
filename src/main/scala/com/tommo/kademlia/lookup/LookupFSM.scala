package com.tommo.kademlia.lookup

import scala.collection.immutable.{TreeMap, SortedMap}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.{ActorNode, Request}
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.protocol.RequestSenderActor.NodeRequest
import LookupFSM._

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.FSM

abstract class LookupFSM(selfId: Id, kclosestRef: ActorRef, reqSender: ActorRef, kBucketSize: Int, alpha: Int, roundTimeOut: FiniteDuration) extends FSM[State, Data] with ActorLogging {
  import context._
  
  startWith(Initial, Empty)

  when(Initial) {
    case Event(searchid: Id, _) =>
      kclosestRef ! getRequest(searchid, kBucketSize)
      goto(WaitForLocalKclosest) using Lookup(searchid, sender)
  }

  when(WaitForLocalKclosest)(localKClosestReq) 

  when(QueryNode)(remoteReplySF orElse {
    case Event(Start, data: QueryNodeData) =>
      sendRequest(data.toQuery, data.req.id)
      stay using data.copy(toQuery = Map(), querying = data.querying ++ data.toQuery)
    case Event(RoundEnd, data: QueryNodeData) => goto(GatherNode) using data
  })

  when(GatherNode)(remoteReplySF orElse { // it's possible to receive a kclosest reply while in this state before the start event
    case Event(Start, queryData: QueryNodeData) => {
      import queryData._

      if (seen.isEmpty) { // TODO queried all the nodes but none of them responded before the round timeout
        log.warning("All of the nodes are queried, but none of them responded before the round timeout. Try increasing the round timeout.")
        goto(Finalize) using Result(queryData.req, List())
      } else {
        val nextRound = currRound + 1
        val closest = seen.head

        closest match {
          case (_, NodeQuery(_, round, _)) if round > currRound && !lastRound =>
            goto(QueryNode) using takeAndUpdate(queryData, alpha).copy(currRound = nextRound)
          case _ if lastRound =>
            val kClosest = seen.take(kBucketSize)

            kClosest.exists(Function.tupled((id, node) => !node.respond)) match { // keep querying since not all of the previous request responded
              case true => goto(QueryNode) using takeAndUpdate(queryData, kBucketSize).copy(currRound = nextRound, lastRound = true)
              case false => goto(Finalize) using Result(queryData.req, kClosest.toList.map(Function.tupled((id, node) => ActorNode(node.ref, id))))
            }

          case _ =>
            goto(QueryNode) using takeAndUpdate(queryData, kBucketSize).copy(currRound = nextRound, lastRound = true)
        }
      }
    }
  })

  when(Finalize) {
    case Event(Start, Result(req, kclosest)) =>
      req.sender ! returnResultsAs(kclosest)
      stop()
  }

  onTransition {
    case _ -> QueryNode => initStateTimer("startQueryNode") // uses from state receive handler instead of to state;
    case QueryNode -> GatherNode => initStateTimer("startGatherNode") 
    case QueryNode -> Finalize => initStateTimer("startFinalize") 
  }
  
  def initStateTimer(name: String) = setTimer(name, Start, 1 micros, false)
  
  def localKclosestState(localReply: { val nodes: List[ActorNode] }, req: Lookup): State = {
      val qd = QueryNodeData(req, seen = TreeMap(localReply.nodes.map(actorNodeToKeyPair(_, round = 1)): _*)(new req.id.SelfOrder), currRound = 1)
      goto(QueryNode) using takeAndUpdate(qd, alpha)
  }
  
  def kclosestState(reply: { val nodes: List[ActorNode]; val senderId: Id }, qd: QueryNodeData): State = { // TODO all nodes might be received before round timeout go to next state if it does
      import qd._
      import reply._
    
      val nextRound = currRound + 1
      val newlySeen = nodes.filter(x => !seen.exists(_._1 == x.id)).map(actorNodeToKeyPair(_, round = nextRound))
      val updateQuery = querying - senderId
      
      val updateSeen = seen ++ newlySeen + (senderId -> querying.get(senderId).get.copy(respond = true))
      stay using qd.copy(querying = updateQuery, seen = updateSeen)
  }
  
  def localKClosestReq: StateFunction
  
  def remoteReplySF: StateFunction
    
  private def sendRequest(toQuery: Map[Id, NodeQuery], lookupId: Id) {
    toQuery.foreach { case (_, NodeQuery(ref, _, _)) => reqSender ! NodeRequest(ref, getRequest(lookupId, kBucketSize)) }
    setTimer("roundTimer", RoundEnd, roundTimeOut, false)
  }
  
  def getRequest(lookupId: Id, k: Int): Request
  
  def returnResultsAs(kclosest: List[ActorNode]): Any = kclosest
}

object LookupFSM {
  def actorNodeToKeyPair(node: ActorNode, round: Int = 1, respond: Boolean = false) = (node.id, NodeQuery(node.ref, round, respond))

  def takeAndUpdate(qd: QueryNodeData, n: Int) = {
    val newtoQuerySeen = takeUnqueried(n, qd.seen)
    qd.copy(toQuery = newtoQuerySeen._1, seen = newtoQuerySeen._2)
  }

  private def takeUnqueried(n: Int, seen: SortedMap[Id, NodeQuery]) = {
    val toQuery = seen.filter(Function.tupled((id, node) => !node.respond)).take(n)
    val updatedSeen = seen -- toQuery.keys
    (toQuery, updatedSeen)
  }

  trait State
  case object Initial extends State
  case object WaitForLocalKclosest extends State
  case object QueryNode extends State
  case object GatherNode extends State
  case object Finalize extends State

  trait Data
  case object Empty extends Data
  case class Lookup(id: Id, sender: ActorRef) extends Data
  case class QueryNodeData(req: Lookup, toQuery: Map[Id, NodeQuery] = Map(), seen: SortedMap[Id, NodeQuery], querying: Map[Id, NodeQuery] = Map(), currRound: Int = 1, lastRound: Boolean = false) extends Data
  case class NodeQuery(ref: ActorRef, round: Int, respond: Boolean = false)
  case class Result(req: Lookup, kClosest: List[ActorNode]) extends Data

  case object Start
  case object RoundEnd
}