package com.tommo.kademlia.misc.time

trait Clock {
	import Clock._
	def getTime(): Epoch 
}

object Clock {
  type Epoch = Long
}