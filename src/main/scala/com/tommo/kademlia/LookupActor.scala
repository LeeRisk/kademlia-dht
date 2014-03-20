package com.tommo.kademlia

import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id

import LookupActor._
import scala.collection.immutable.TreeMap
import akka.actor.{ ActorRef, FSM }
import akka.pattern.{ ask, pipe }

class LookupActor(kBucketActor: ActorRef)(implicit val config: KadConfig) extends FSM[State, Data] {
  import context._

  implicit val timeout: akka.util.Timeout = config.responseTimeout // todo 
  
  println(config.roundTimeOut);
  
  startWith(Initial, Empty)

  when(Initial) {
    case Event(id: Id, _) =>
      kBucketActor ! GetKClosest(id: Id, config.kBucketSize)
      goto(QueryKBucket) using QueryKBucketData(id)
  }

  when(QueryKBucket) {
    case Event(KClosest(nodes), data: QueryKBucketData) =>
      val seen = TreeMap(nodes.map(actorNodeToKeyPair(_)): _*)(new data.id.Order)
      goto(QueryNode) using QueryNodeData(data.id, seen.take(config.concurrency), seen)
  }

  onTransition {
    case QueryKBucket -> QueryNode =>
      (nextStateData: @unchecked) match {
        case QueryNodeData(id, toQuery, seen) => {
          val k = config.kBucketSize
          toQuery.foreach {
            case (_, NodeQuery(ref, _)) => (ref ? GetKClosest(id, k)).mapTo[KClosest] pipeTo self
          }
        }
      }
  }

  when(QueryNode, config.roundTimeOut) {
    case Event(KClosestRemote(sender, nodes), data @ QueryNodeData(id, query, seen)) => {
      val updateNodeStatus = query + actorNodeToKeyPair(sender, true)

      val nodesNotSeen = nodes.filter(x => !seen.exists(_._1 == x.id)).map(actorNodeToKeyPair(_)) // only copy nodes with id's not already in the seen map
      val updatedSeen = seen ++ nodesNotSeen
      stay using data.copy(querying = updateNodeStatus, seen = updatedSeen)
    }
    case Event(StateTimeout, _) => goto(GatherNode)
  }
  
  when(GatherNode) {
    case _ => stay using Empty
  }

}

object LookupActor {
  private[kademlia] def actorNodeToKeyPair(node: ActorNode, success: Boolean = false) = (node.id, NodeQuery(node.actor, success))
  case class GetKClosest(id: Id, k: Int)

  case class KClosest(nodes: List[ActorNode])
  case class KClosestRemote(sender: ActorNode, nodes: List[ActorNode])

  sealed trait State
  case object Initial extends State
  case object QueryKBucket extends State
  case object QueryNode extends State
  case object GatherNode extends State

  case class NodeQuery(ref: ActorRef, success: Boolean = false)

  sealed trait Data
  case object Empty extends Data
  case class QueryKBucketData(id: Id) extends Data
  case class QueryNodeData(id: Id, querying: Map[Id, NodeQuery], seen: TreeMap[Id, NodeQuery]) extends Data
}