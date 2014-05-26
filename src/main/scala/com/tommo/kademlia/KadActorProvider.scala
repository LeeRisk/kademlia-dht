package com.tommo.kademlia

import com.tommo.kademlia.identity.Id
import Kademlia._
import akka.actor.Actor

trait KadActorProvider {
	def newNetwork(self: Id)(implicit config: KadConfig): Actor = new KadActor(self)
	def joinNetwork(self: Id, existing: ExistingHost)(implicit config: KadConfig): Actor = new KadActor(self)
}

