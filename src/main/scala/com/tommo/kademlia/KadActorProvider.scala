package com.tommo.kademlia

import com.tommo.kademlia.identity.Id

import akka.actor.Actor

trait KadActorProvider {
	def newKadActor(self: Id)(implicit config: KadConfig): Actor = new KadActor(self)
}

