package com.tommo.kademlia

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.{ KBucketSetActor, KBucketRefresher}
import com.tommo.kademlia.protocol.{ RequestDispatcher, ActorNode, RequestHandler }
import com.tommo.kademlia.protocol.RequestDispatcher.NodeRequest
import com.tommo.kademlia.store.StoreActor
import com.tommo.kademlia.util.RefreshActor
import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.lookup.LookupValueFSM
import com.tommo.kademlia.protocol.Message.AuthSenderRequest
import com.tommo.kademlia.lookup.LookupDispatcher
import com.tommo.kademlia.store.StoreActor.Insert


import akka.actor.{ Actor, Props }

class KadActor[V](selfId: Id)(implicit config: KadConfig) extends Actor {
  this: KBucketRefresher.Provider with LookupDispatcher.Provider with KBucketSetActor.Provider with RequestDispatcher.Provider with StoreActor.Provider[V] =>

  val selfNode = ActorNode(self, selfId)
  val refresher = context.actorOf(Props(new RefreshActor() with Clock))

  val kBucketSet = context.actorOf(Props(newKBucketSetActor(selfNode)))
  val nodeLookupDispatcher = context.actorOf(Props(newLookupNodeDispatcher(selfNode, kBucketSet)))
  val store = context.actorOf(Props(storeActor(selfNode, kBucketSet, refresher, nodeLookupDispatcher)))
  val valueLookupDispatcher = context.actorOf(Props(newLookupValueDispatcher(selfNode, kBucketSet, store)))
  val staleKSetRefresh = context.actorOf(Props(newKBucketRefresher(nodeLookupDispatcher, valueLookupDispatcher, kBucketSet, refresher)))
  val requestHandler = context.actorOf(Props(new RequestHandler(selfNode.ref, kBucketSet, store)))
  val requestDispatcher = context.actorOf(Props(newRequestDispatcher(selfNode, kBucketSet, requestHandler, config.requestTimeOut)))
  
  def receive = {
    case authReq: AuthSenderRequest => requestDispatcher forward authReq
    case request: NodeRequest => requestDispatcher forward request
    case lookup: LookupValueFSM.FindValue => valueLookupDispatcher forward lookup
    case insert: Insert[V] => store forward insert
  }
}
