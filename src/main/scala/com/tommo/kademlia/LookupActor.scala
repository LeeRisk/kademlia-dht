package com.tommo.kademlia

import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id

import LookupActor._

import akka.actor.{ ActorRef, FSM }
import akka.pattern.{ ask, pipe }

class LookupActor(kBucketActor: ActorRef)(implicit val config: KadConfig) extends FSM[State, Data] {
  import context._

  implicit val timeout: akka.util.Timeout = config.timeout // todo 
  
  startWith(Initial, Empty)

  when(Initial) {
    case Event(id: Id, _) =>
      kBucketActor ! GetKClosest(id: Id, config.kBucketSize)
      goto(QueryKBucketSet) using QueryKBucketSetData(id)
  }

  when(QueryKBucketSet) {
    case Event(KClosest(nodes), data: QueryKBucketSetData) =>
      goto(QueryNode) using QueryNodeData(data.id, nodes)
  }

  onTransition {
    case QueryKBucketSet -> QueryNode =>
      (nextStateData: @unchecked) match {
        case QueryNodeData(id, nodes, _) => {
          val k = config.kBucketSize
          val toQuery = nodes.take(config.concurrency)
          toQuery.foreach(node => (node.actor ? GetKClosest(id, k)).mapTo[KClosest] pipeTo self) 
        }
      }
  }

  when(QueryNode) {
    case _ => stay using Empty
  }

}

object LookupActor {
  case class GetKClosest(id: Id, k: Int) 
  case class KClosest(nodes: List[ActorNode])

  sealed trait State
  case object Initial extends State
  case object QueryKBucketSet extends State
  case object QueryNode extends State

  sealed trait Data
  case object Empty extends Data
  case class QueryKBucketSetData(id: Id) extends Data
  case class QueryNodeData(id: Id, nodes: List[ActorNode], numResponse: Int = 0) extends Data
}