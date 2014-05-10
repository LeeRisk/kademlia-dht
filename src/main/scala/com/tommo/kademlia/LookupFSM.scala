package com.tommo.kademlia

import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.protocol.RequestSenderActor._

import LookupFSM._
import scala.collection.immutable.{ SortedMap, TreeMap }
import akka.actor.{ ActorRef, FSM, ActorLogging }
import scala.concurrent.duration._

class LookupFSM(selfId: Id, kBucketActor: ActorRef, reqSender: ActorRef, kBucketSize: Int, alpha: Int, roundTimeOut: FiniteDuration) extends FSM[State, Data] with ActorLogging {
  import context._

  startWith(Initial, Empty)

  when(Initial) {
    case Event(id: Id, _) =>
      kBucketActor ! GetKClosest(id, kBucketSize)
      goto(QueryKBucket) using QueryKBucketData(Lookup(id, sender))
  }

  when(QueryKBucket) {
    case Event(KClosest(_, nodes), QueryKBucketData(req)) =>
      val qd = QueryNodeData(req, seen = TreeMap(nodes.map(actorNodeToKeyPair(_, round = 1)): _*)(new req.id.SelfOrder), currRound = 1)
      goto(QueryNode) using takeAndUpdate(qd, alpha)
  }

  when(QueryNode)(kClosestReplyPf orElse {
    case Event(Start, data: QueryNodeData) =>
      sendRequest(data.toQuery, data.req.id)
      stay using data.copy(toQuery = Map(), querying = data.querying ++ data.toQuery)
    case Event(RoundEnd, data: QueryNodeData) => goto(GatherNode) using data
  })

  when(GatherNode)(kClosestReplyPf orElse { // it's possible to receive a kclosest reply while in this state before the start event
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
      req.sender ! kclosest
      stop()
  }

  private def kClosestReplyPf: StateFunction = {
    case Event(KClosestReply(sender, nodes), data @ QueryNodeData(id, _, seen, querying, currRound, _)) =>
      val nextRound = currRound + 1
      val newlySeen = nodes.filter(x => !seen.exists(_._1 == x.id)).map(actorNodeToKeyPair(_, round = nextRound))
      val updateQuery = querying - sender

      val updateSeen = seen ++ newlySeen + (sender -> querying.get(sender).get.copy(respond = true))
      stay using data.copy(querying = updateQuery, seen = updateSeen)
  }

  onTransition {
    case _ -> QueryNode => setTimer("startQueryNode", Start, 1 micros, false) // uses from state receive handler instead of to state;
    case QueryNode -> GatherNode => setTimer("startGatherNode", Start, 1 micros, false)
    case QueryNode -> Finalize => setTimer("startFinalize", Start, 1 micros, false)

  }

  private def sendRequest(toQuery: Map[Id, NodeQuery], lookupId: Id) {
    toQuery.foreach { case (_, NodeQuery(ref, _, _)) => reqSender ! NodeRequest(ref, KClosestRequest(selfId, lookupId, kBucketSize)) }
    setTimer("roundTimer", RoundEnd, roundTimeOut, false)
  }
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

  case class Lookup(id: Id, sender: ActorRef)

  sealed trait State
  case object Initial extends State
  case object QueryKBucket extends State
  case object QueryNode extends State
  case object GatherNode extends State
  case object Finalize extends State

  sealed trait Data
  case object Empty extends Data
  case class QueryKBucketData(req: Lookup) extends Data
  case class QueryNodeData(req: Lookup, toQuery: Map[Id, NodeQuery] = Map(), seen: SortedMap[Id, NodeQuery], querying: Map[Id, NodeQuery] = Map(), currRound: Int = 1, lastRound: Boolean = false) extends Data
  case class NodeQuery(ref: ActorRef, round: Int, respond: Boolean = false)
  case class Result(req: Lookup, kClosest: List[ActorNode]) extends Data

  case object Start
  case object RoundEnd
}