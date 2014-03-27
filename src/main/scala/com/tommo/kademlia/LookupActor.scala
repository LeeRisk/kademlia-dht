package com.tommo.kademlia

import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id

import LookupActor._
import scala.collection.immutable.{SortedMap, TreeMap}
import akka.actor.{ActorRef, FSM}
import akka.actor.Status.Failure
import akka.pattern.{ask, pipe, AskTimeoutException}
import scala.concurrent.duration._

class LookupActor(kBucketActor: ActorRef)(implicit val config: KadConfig) extends FSM[State, Data] {
  import context._
  import config._

  implicit val timeout: akka.util.Timeout = config.roundTimeOut

  startWith(Initial, Empty)

  when(Initial) {
    case Event(id: Id, _) =>
      kBucketActor ! GetKClosest(id: Id, kBucketSize)
      goto(QueryKBucket) using QueryKBucketData(id)
  }

  when(QueryKBucket) {
    case Event(KClosest(nodes), data: QueryKBucketData) =>
      val newRound = 1
      val seen = TreeMap(nodes.map(actorNodeToKeyPair(_, round = newRound)): _*)((new data.id.Order).reverse)
      goto(QueryNode) using QueryNodeData(data.id, seen.take(config.concurrency), seen, currRound = newRound)
  }

  onTransition {
    case QueryKBucket -> QueryNode =>
      (nextStateData: @unchecked) match {
        case QueryNodeData(id, toQuery, _, _, _) =>
          toQuery.foreach {
            case (_, NodeQuery(ref, _, _)) => (ref ? GetKClosest(id, kBucketSize)).mapTo[KClosest] pipeTo self
          }
      }
  }

  when(QueryNode)(transform {
    case Event(KClosestRemote(sender, nodes), data @ QueryNodeData(id, query, seen, count, currRound)) =>
      val updateNodeStatus = query + actorNodeToKeyPair(sender, true, currRound)
      val nodesNotSeen = nodes.filter(x => !seen.exists(_._1 == x.id)).map(actorNodeToKeyPair(_, round = currRound + 1)) // only copy nodes with id's not already in the seen map
      val updatedSeen = seen ++ nodesNotSeen
      stay using data.copy(querying = updateNodeStatus, seen = updatedSeen)
    case Event(Failure(_: AskTimeoutException), data @ QueryNodeData(_, _, _, count, _)) => { stay using data.copy(responseCount = count + 1) }
  } using {
    case s if numResponseEqQuerying(s.stateData) =>
      s.copy(stateName = GatherNode)
  })

  private def numResponseEqQuerying(data: Data) = data match {
    case QueryNodeData(_, querying, _, responseCount, _) if querying.size == responseCount => true
    case _ => false
  }

  onTransition {
    case QueryNode -> GatherNode => self ! InitiateGather
  }

  when(GatherNode) {
    case Event(InitiateGather, data @ QueryNodeData(_, query, seen, _, currRound)) => {
      val nextRound = currRound + 1
      val kClosestNotYetQueried = seen.take(kBucketSize).filter(Function.tupled((id, node) => node.queried == false && node.round == nextRound))
      goto(QueryNode) using data.copy(querying = kClosestNotYetQueried, seen = seen ++ query, responseCount = 0, currRound = nextRound)
    }
  }
}

object LookupActor {
  private[kademlia] def actorNodeToKeyPair(node: ActorNode, queried: Boolean = false, round: Int = 1) = (node.id, NodeQuery(node.actor, queried, round))

  private[kademlia] object InitiateGather

  sealed trait State
  case object Initial extends State
  case object QueryKBucket extends State
  case object QueryNode extends State
  case object GatherNode extends State

  sealed trait Data
  case object Empty extends Data
  case class QueryKBucketData(id: Id) extends Data
  case class QueryNodeData(id: Id, querying: Map[Id, NodeQuery] = Map(), seen: SortedMap[Id, NodeQuery], responseCount: Int = 0, currRound: Int = 1) extends Data
  case class NodeQuery(ref: ActorRef, queried: Boolean = false, round: Int)

  case class KClosest(nodes: List[ActorNode]) // todo belongs in diff class
  case class KClosestRemote(sender: ActorNode, nodes: List[ActorNode])
  case class GetKClosest(id: Id, k: Int)

}