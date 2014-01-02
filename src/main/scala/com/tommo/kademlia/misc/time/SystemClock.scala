package com.tommo.kademlia.misc.time

trait SystemClock extends Clock {
	def getTime() = System.currentTimeMillis()
}

