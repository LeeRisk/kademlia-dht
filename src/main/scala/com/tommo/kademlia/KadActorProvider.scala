package com.tommo.kademlia

import akka.actor.Actor

import Kademlia.ExistingHost
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.{ KBucketSetActor, KBucketRefresher}
import com.tommo.kademlia.protocol.RequestDispatcher
import com.tommo.kademlia.store.StoreActor
import com.tommo.kademlia.lookup.LookupDispatcher
import com.tommo.kademlia.store.StoreActor.{ Get, Insert }

trait KadActorProvider[V] {
	def newNetwork(self: Id)(implicit config: KadConfig): Actor = 
	  new KadActor[V](self) with KBucketRefresher.Provider with LookupDispatcher.Provider with KBucketSetActor.Provider with RequestDispatcher.Provider with StoreActor.Provider[V]
	
	def joinNetwork(self: Id, existing: ExistingHost)(implicit config: KadConfig): Actor = null; //new KadActor(self)
}

