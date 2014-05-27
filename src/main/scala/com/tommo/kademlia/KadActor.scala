package com.tommo.kademlia

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.lookup.LookupDispatcher
import com.tommo.kademlia.routing.KBucketSetActor
import com.tommo.kademlia.protocol.{ RequestDispatcher, ActorNode, RequestHandler }
import com.tommo.kademlia.protocol.RequestDispatcher.NodeRequest
import com.tommo.kademlia.store.StoreActor
import com.tommo.kademlia.util.RefreshActor
import com.tommo.kademlia.misc.time.Clock

import com.tommo.kademlia.protocol.Message.AuthSenderRequest
import com.tommo.kademlia.lookup.LookupValue
import com.tommo.kademlia.store.StoreActor.{ Get, Insert }
import com.tommo.kademlia.routing.KBucketSetActor.Add

import akka.actor.{ Actor, Props }

class KadActor[V](selfId: Id)(implicit config: KadConfig) extends Actor {
  this: LookupDispatcher.Provider with KBucketSetActor.Provider with RequestDispatcher.Provider with StoreActor.Provider[V] =>

  val selfNode = ActorNode(self, selfId)
  
  val refresher = context.actorOf(Props(new RefreshActor() with Clock))
  
  val kBucketSet = context.actorOf(Props(newKBucketSetActor(selfNode)))
  val store = context.actorOf(Props(storeActor(selfNode, kBucketSet, refresher, lookupDispatcher)))
  val requestHandler = context.actorOf(Props(new RequestHandler(selfNode.ref, kBucketSet, store)))
  val requestDispatcher = context.actorOf(Props(newRequestDispatcher(selfNode, kBucketSet, requestHandler, config.requestTimeOut)))
  val lookupDispatcher = context.actorOf(Props(newLookupDispatcher(selfNode, kBucketSet, refresher)))
  

  def receive = {
    case authReq: AuthSenderRequest => requestDispatcher forward authReq
    case lookup: LookupValue.FindValue => lookupDispatcher forward lookup
    case insert: Insert[V] => store forward insert
    case get: Get => store forward get // TODO seperate store lookup from dispatcher so it doesnt depend on store
    case request: NodeRequest => requestDispatcher forward request
  }
}
