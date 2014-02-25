package com.tommo.kademlia

import com.tommo.kademlia.identity.Id
import LookupActor._

import akka.actor.ActorRef
import akka.actor.FSM

class LookupActor(kBucketActor: ActorRef)(implicit val config: KadConfig) extends FSM[State, Data] {

  startWith(Initial, Empty)

  when(Initial) {
    case Event(id: Id, _) =>
      kBucketActor ! GetKClosest(id: Id, config.kBucketSize)
      goto(QueryKBucketSet) using QueryKBucketData(id)
  }
  
  when(QueryKBucketSet) {
    case a => stay using Empty
  }
}

object LookupActor {
  case class GetKClosest(id: Id, k: Int) // TODO belongs in kBucketActor

  sealed trait State
  case object Initial extends State
  case object QueryKBucketSet extends State

  sealed trait Data
  case object Empty extends Data
  case class QueryKBucketData(id: Id) extends Data
}