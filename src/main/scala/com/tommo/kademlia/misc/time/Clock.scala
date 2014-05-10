package com.tommo.kademlia.misc.time

trait Clock {
	import Clock._
	def getTime(): Epoch = System.currentTimeMillis()
}

object Clock {
  type Epoch = Long
}