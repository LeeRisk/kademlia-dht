package com.tommo.kademlia.util

import akka.actor.Actor
import scala.concurrent.duration._
import scala.collection.mutable.LinkedHashMap
import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.misc.time.Clock._
import TimedRefresher._

trait TimedRefresher[T] {
	this: Actor with Clock =>
	
	var mostRecent = LinkedHashMap[T, Epoch]()
	  
	val refreshCycle: FiniteDuration

	self ! InitRefreshTimer
	
	val onRefreshFn: T => Unit
	
	def add(key: T) {
	  mostRecent.put(key, getTime())
	} 
}

object TimedRefresher {
  case object InitRefreshTimer
}