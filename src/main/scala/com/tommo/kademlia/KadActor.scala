package com.tommo.kademlia

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.{ KBucketSetActor, KBucketRefresher }
import com.tommo.kademlia.protocol.{ RequestDispatcher, ActorNode, RequestHandler }
import com.tommo.kademlia.protocol.RequestDispatcher.NodeRequest
import com.tommo.kademlia.store.StoreActor
import com.tommo.kademlia.util.RefreshActor
import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.lookup.LookupValueFSM
import com.tommo.kademlia.protocol.Message.AuthReceiverRequest
import com.tommo.kademlia.lookup.LookupDispatcher
import com.tommo.kademlia.store.StoreActor.Insert
import KadActor._

import akka.actor.{ FSM, Props, ActorRef }

private[kademlia] object KadActor {
  trait State
  case object Running extends State

  trait Data
  case object Empty extends Data
}

class KadActor[V](selfId: Id)(implicit config: KadConfig) extends FSM[State, Data] {
  this: KBucketRefresher.Provider with LookupDispatcher.Provider with KBucketSetActor.Provider with RequestDispatcher.Provider with StoreActor.Provider[V] =>

  val selfNode = ActorNode(self, selfId)

  val kBucketSet = context.actorOf(Props(newKBucketSetActor(selfNode)))
  val nodeLookupDispatcher = context.actorOf(Props(newLookupNodeDispatcher(selfNode, kBucketSet)))
  context.actorOf(Props(newKBucketRefresher(nodeLookupDispatcher, valueLookupDispatcher, kBucketSet, refresher)))

  val refresher = context.actorOf(Props(new RefreshActor() with Clock))
  val store = context.actorOf(Props(storeActor(selfNode, kBucketSet, refresher, nodeLookupDispatcher)))
  val valueLookupDispatcher = context.actorOf(Props(newLookupValueDispatcher(selfNode, kBucketSet, store)))
  val requestHandler = context.actorOf(Props(new RequestHandler(kBucketSet, store)))
  val requestDispatcher = context.actorOf(Props(newRequestDispatcher(selfNode, kBucketSet, requestHandler, config.requestTimeOut)))

  startWith(Running, Empty)

  when(Running)(nodeRequestSF orElse runningPF.andThen(x => stay))

  protected def runningPF(): PartialFunction[Event, Unit] = {
    case Event(authReq: AuthReceiverRequest, _) => requestDispatcher forward authReq
    case Event(lookup: LookupValueFSM.FindValue, _) => valueLookupDispatcher forward lookup
    case Event(insert: Insert[V], _) => store forward insert
  }

  protected def nodeRequestSF(): StateFunction = {
    case Event(request: NodeRequest, _) =>
      requestDispatcher forward request
      stay
  }
}

private[kademlia] object JoiningKadActor {
  case object Joining extends State
  case object WaitForExisting extends State
}

class JoiningKadActor[V](selfId: Id)(implicit config: KadConfig) extends KadActor[V](selfId) {
  this: KBucketRefresher.Provider with LookupDispatcher.Provider with KBucketSetActor.Provider with RequestDispatcher.Provider with StoreActor.Provider[V] =>

  import JoiningKadActor._
  import com.tommo.kademlia.protocol.Message.{ PingRequest, AckReply }
  import com.tommo.kademlia.lookup.LookupNodeFSM.{ FindKClosest, Result }
  import com.tommo.kademlia.routing.KBucketSetActor.{ GetLowestNonEmpty, LowestNonEmpty, GetRandomId, RandomId }

  startWith(WaitForExisting, Empty)

  when(WaitForExisting) {
    case Event(ref: ActorRef, Empty) =>
      requestDispatcher ! NodeRequest(ref, PingRequest)
      goto(Joining)
  }

  when(Joining)(nodeRequestSF orElse {
    case Event(AckReply, Empty) =>
      nodeLookupDispatcher ! FindKClosest(selfId)
      stay
    case Event(Result(id, _), Empty) =>
      kBucketSet ! GetLowestNonEmpty
      stay
    case Event(LowestNonEmpty(index), Empty) =>
      kBucketSet ! GetRandomId((index until config.kBucketSize).toList)
      stay
    case Event(RandomId(randIds), Empty) =>
      randIds.map(x => FindKClosest(x._2)).foreach(sendMsgIgnoreReply(nodeLookupDispatcher,_))
      goto(Running)
  })


  private def sendMsgIgnoreReply(ref: ActorRef, msg: Any) = ref.tell(msg, context.system.deadLetters)
} 




