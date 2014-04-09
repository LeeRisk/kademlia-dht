package com.tommo.kademlia

import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id

import LookupActor._
import scala.collection.immutable.{ SortedMap, TreeMap }
import akka.actor.{ ActorRef, FSM }
import akka.actor.Status.Failure
import akka.pattern.{ ask, pipe, AskTimeoutException }
import scala.concurrent.duration._

class LookupActor(kBucketActor: ActorRef)(implicit val config: KadConfig) extends FSM[State, Data] {
  import context._
  import config._

  implicit val timeout: akka.util.Timeout = config.roundTimeOut

  startWith(Initial, Empty)

  when(Initial) {
    case Event(id: Id, _) =>
      kBucketActor ! GetKClosest(id: Id, kBucketSize)
      goto(QueryKBucket) using QueryKBucketData(KClosestRequest(id, sender))
  }

  when(QueryKBucket) {
    case Event(KClosest(nodes), QueryKBucketData(req)) =>
      val newRound = 1
      val seen = TreeMap(nodes.map(actorNodeToKeyPair(_, round = newRound)): _*)((new req.id.Order).reverse)
      goto(QueryNode) using QueryNodeData(req, seen.take(config.concurrency), seen, currRound = newRound)
  }

  when(QueryNode)(transform {
    case Event(KClosestRemote(sender, nodes), data @ QueryNodeData(id, query, seen, count, currRound, _)) =>
      val updateNodeStatus = query + actorNodeToKeyPair(sender, true, currRound)
      val nodesNotSeen = nodes.filter(x => !seen.exists(_._1 == x.id)).map(actorNodeToKeyPair(_, round = currRound + 1))
      val updatedSeen = seen ++ nodesNotSeen
      stay using data.copy(querying = updateNodeStatus, seen = updatedSeen)
    case Event(Failure(_: AskTimeoutException), data) => stay using data
  } using {
    case s =>  
      (s.stateData: @unchecked) match {
        case data: QueryNodeData  =>
          incrementResponseCount(data) match {
            case incS: QueryNodeData if numResponseEqQuerying(incS) => s.copy(stateName = GatherNode, stateData = incS)
            case s => stay using s
          }
      }
  })

  when(GatherNode) {
    case Event(Start, queryData: QueryNodeData) => {
      import queryData._
      val closest = seen.head
      val nextRound = nextQueryRound(queryData)
      closest match {
        case (_, NodeQuery(_, _, round)) if round > currRound && !lastRound => goto(QueryNode) using nextRound.copy(querying = nextRound.seen.take(concurrency).filter(notYetQueried) ++ nextRound.querying)
        case _ if lastRound => goto(Finalize) using nextRound
        case _ => goto(QueryNode) using nextRound.copy(querying = nextRound.seen.take(kBucketSize).filter(notYetQueried), lastRound = true)
      }
    }
  }

  when(Finalize) {
    case Event(Start, QueryNodeData(req, _, seen, _, _, _)) => 
      req.sender ! seen.toList.map(_._2)
      goto(Initial) using Empty
  }

  onTransition {
    case QueryNode -> GatherNode => self ! Start
    case _ -> QueryNode => (nextStateData: @unchecked) match { case s: QueryNodeData => sendRequestInQuerying(s) }
    case GatherNode -> Finalize => self ! Start
  }

  private def nextQueryRound(queryData: QueryNodeData) = {
    import queryData._
    queryData.copy(querying = querying.filter(notYetQueried), seen = seen ++ querying, responseCount = 0, currRound = currRound + 1)
  }

  private def incrementResponseCount(queryData: QueryNodeData) = {
    import queryData._
    queryData.copy(responseCount = responseCount + 1)
  }

  private def numResponseEqQuerying(data: Data) = data match {
    case QueryNodeData(_, querying, _, responseCount, _, _) if querying.size == responseCount => true
    case _ => false
  }

  private def sendRequestInQuerying(queryData: QueryNodeData) { // send requests to nodes in querying that have not been queried yet
    import queryData._
    querying.foreach { case (_, NodeQuery(ref, queried, _)) => (ref ? GetKClosest(req.id, kBucketSize)).mapTo[KClosest] pipeTo self }
  }
}

object LookupActor {
  private[kademlia] def actorNodeToKeyPair(node: ActorNode, queried: Boolean = false, round: Int = 1) = (node.id, NodeQuery(node.ref, queried, round))
  private[kademlia] case object Start
  private[kademlia] case class KClosestRequest(id: Id, sender: ActorRef)

  private val notYetQueried: ((Id, NodeQuery)) => Boolean = Function.tupled((id, node) => node.queried == false)

  sealed trait State
  case object Initial extends State
  case object QueryKBucket extends State
  case object QueryNode extends State
  case object GatherNode extends State
  case object Finalize extends State

  sealed trait Data
  case object Empty extends Data
  case class QueryKBucketData(req: KClosestRequest) extends Data
  case class QueryNodeData(req: KClosestRequest, querying: Map[Id, NodeQuery] = Map(), seen: SortedMap[Id, NodeQuery], responseCount: Int = 0, currRound: Int = 1, lastRound: Boolean = false) extends Data
  case class NodeQuery(ref: ActorRef, queried: Boolean = false, round: Int)

  case class KClosest(nodes: List[ActorNode]) // TODO belongs in diff class
  case class KClosestRemote(sender: ActorNode, nodes: List[ActorNode])
  case class GetKClosest(id: Id, k: Int)

}