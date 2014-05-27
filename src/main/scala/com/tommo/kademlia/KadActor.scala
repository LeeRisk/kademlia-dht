package com.tommo.kademlia

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.lookup.LookupDispatcher
import com.tommo.kademlia.routing.KBucketSetActor
import com.tommo.kademlia.protocol.RequestDispatcher
import com.tommo.kademlia.store.StoreActor
import akka.actor.Actor

class KadActor(selfId: Id)(implicit config: KadConfig) extends Actor {
  self: LookupDispatcher.Provider with KBucketSetActor.Provider 
  			with RequestDispatcher.Provider with StoreActor.Provider =>

//    val lookupDispatcherRef = 
    
    
    
  def receive = {
    case _ =>
  }

}