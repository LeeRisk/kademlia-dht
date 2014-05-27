package com.tommo.kademlia.lookup

import scala.collection.immutable.{ TreeMap, SortedMap }
import scala.concurrent.duration.{ DurationInt, FiniteDuration }
import akka.actor.{ ActorLogging, ActorRef, FSM, Actor }

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.{ ActorNode, Request }
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.protocol.RequestDispatcher.NodeRequest
import LookupNodeFSM._

class LookupNodeFSM(selfNode: ActorNode, kBucketSetRef: ActorRef, kBucketSize: Int, alpha: Int, roundTimeOut: FiniteDuration) extends FSM[State, Data] with ActorLogging {
  import context._

  startWith(Initial, Empty)

  when(Initial)(queryKbucketSF)

  final def queryKbucketSF: StateFunction = {
    case Event(FindKClosest(searchid), _) =>
      kBucketSetRef ! KClosestRequest(searchid, kBucketSize)
      goto(WaitForLocalKclosest) using Lookup(searchid, sender)
  }

  when(WaitForLocalKclosest) {
    case Event(reply: KClosestReply, req: Lookup) =>
      val nodes = selfNode :: reply.nodes // add self since kBucketSet doesn't know itself
      val qd = QueryNodeData(req, seen = TreeMap(nodes.map(actorNodeToKeyPair(_, round = 1)): _*)(new req.id.SelfOrder), currRound = 1)
      goto(QueryNode) using takeAndUpdate(qd, alpha)
  }

  when(QueryNode)(remoteReplySF orElse {
    case Event(Start, data: QueryNodeData) =>
      sendRequest(data.toQuery, data.req.id)
      stay using data.copy(toQuery = Map(), querying = data.querying ++ data.toQuery)
    case Event(RoundEnd, data: QueryNodeData) => goto(GatherNode) using data
  })

  private def sendRequest(toQuery: Map[Id, NodeQuery], lookupId: Id) {
    toQuery.foreach { case (_, NodeQuery(ref, _, _)) => selfNode.ref ! NodeRequest(ref, remoteKClosest(lookupId, kBucketSize)) }
    setTimer("roundTimer", RoundEnd, roundTimeOut, false)
  }

  def remoteKClosest(lookupId: Id, k: Int): Request = KClosestRequest(lookupId, k)

  when(GatherNode)(remoteReplySF orElse {
    case Event(Start, queryData: QueryNodeData) => {
      import queryData._

      if (seen.isEmpty) { // TODO queried all the nodes but none of them responded before the round timeout
        log.warning("All of the nodes are queried, but none of them responded before the round timeout. Try increasing the round timeout.")
        goto(Finalize) using FinalizeData(queryData.req, List())
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
              case false => goto(Finalize) using FinalizeData(queryData.req, kClosest.toList.map(Function.tupled((id, node) => ActorNode(node.ref, id))))
            }

          case _ =>
            goto(QueryNode) using takeAndUpdate(queryData, kBucketSize).copy(currRound = nextRound, lastRound = true)
        }
      }
    }
  })

  def remoteReplySF: StateFunction = { case Event(reply: KClosestReply, qd: QueryNodeData) => kclosestState(reply, qd) }

  final def kclosestState(reply: KClosestReply, qd: QueryNodeData): State = { // TODO all nodes might be received before round timeout go to next state if it does
    import qd._
    import reply._

    val nextRound = currRound + 1
    val newlySeen = reply.nodes.filter(x => !seen.exists(_._1 == x.id)).map(actorNodeToKeyPair(_, round = nextRound))
    val updateQuery = querying - from

    val updateSeen = seen ++ newlySeen + (from -> querying.get(from).get.copy(respond = true))
    stay using qd.copy(querying = updateQuery, seen = updateSeen)
  }

  when(Finalize) {
    case Event(Start, FinalizeData(req, kclosest)) =>
      req.sender ! returnResultsAs(req.id, kclosest)
      stop()
  }

  def returnResultsAs(id: Id, kclosest: List[ActorNode]): Any = Result(id, kclosest)

  onTransition {
    case _ -> QueryNode => initStateTimer("startQueryNode")
    case QueryNode -> GatherNode => initStateTimer("startGatherNode")
    case QueryNode -> Finalize => initStateTimer("startFinalize")
  }

  def initStateTimer(name: String) = setTimer(name, Start, 1 micros, false)

}

object LookupNodeFSM {
  trait Provider {
    def newLookupNodeActor(selfNode: ActorNode, kBucketSetRef: ActorRef, kBucketSize: Int, alpha: Int, roundTimeOut: FiniteDuration): Actor =
      new LookupNodeFSM(selfNode, kBucketSetRef, kBucketSize, alpha, roundTimeOut)
  }

  case class FindKClosest(searchId: Id)
  case class Result(searchId: Id, kclosest: List[ActorNode])

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
  case class FinalizeData(req: Lookup, kClosest: List[ActorNode]) extends Data

  case object Start
  case object RoundEnd
}