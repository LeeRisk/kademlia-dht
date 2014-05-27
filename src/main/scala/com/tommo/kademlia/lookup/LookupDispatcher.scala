package com.tommo.kademlia.lookup

import akka.actor.{ Actor, ActorRef, Props }
import scala.concurrent.duration._
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.util.EventSource
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.KadConfig

abstract class LookupDispatcher extends Actor with EventSource {
  import LookupDispatcher._

  final def receive = eventSourceReceive orElse lookupReceive

  def lookupReceive: Receive

  protected def broadcast(id: Id) {
    sendEvent(Lookup(id))
  }
}

class NodeLookup(selfNode: ActorNode, kBucketRef: ActorRef)(implicit val config: KadConfig) extends LookupDispatcher {
  this: LookupNodeFSM.Provider =>

  import LookupNodeFSM.FindKClosest
  import config._

  def lookupReceive = {
    case req @ FindKClosest(id) =>
      val nodeFSM = context.actorOf(Props(newLookupNodeActor(selfNode, kBucketRef, kBucketSize, roundConcurrency, roundTimeOut)))
      nodeFSM forward req
      broadcast(id)
  }
}

class ValueLookup(selfNode: ActorNode, kBucketRef: ActorRef, storeRef: ActorRef)(implicit val config: KadConfig) extends LookupDispatcher {
  this: LookupValueFSM.Provider =>

  import config._
  import LookupValueFSM.FindValue

  def lookupReceive = {
    case req @ FindValue(id) =>
      val nodeFSM = context.actorOf(Props(newLookupValueFSM(selfNode, kBucketRef, storeRef, kBucketSize, roundConcurrency, roundTimeOut)))
      nodeFSM forward req
      broadcast(id)
  }
}

object LookupDispatcher {
  case class Lookup(id: Id)

  trait Provider {
    def newLookupNodeDispatcher(selfNode: ActorNode, kBucketRef: ActorRef)(implicit config: KadConfig): Actor =
      new NodeLookup(selfNode, kBucketRef) with LookupNodeFSM.Provider

    def newLookupValueDispatcher(selfNode: ActorNode, kBucketRef: ActorRef, storeRef: ActorRef)(implicit config: KadConfig): Actor =
      new ValueLookup(selfNode, kBucketRef, storeRef) with LookupValueFSM.Provider
  }
}