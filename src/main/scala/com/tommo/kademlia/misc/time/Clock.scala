package com.tommo.kademlia.misc.time

trait Clock {
	def getTime(): Epoch = System.currentTimeMillis()
}
