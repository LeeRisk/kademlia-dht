package com.tommo.kademlia

import com.tommo.kademlia.identity.Id

import akka.pattern.{ ask, pipe }

import akka.actor._
import akka.util.Timeout
class KadActor(id: Id)(implicit config: KadConfig) extends Actor {
  
  def receive = {
    case _ => 
  }
  
}