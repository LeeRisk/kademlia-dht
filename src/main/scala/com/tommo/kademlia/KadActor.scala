package com.tommo.kademlia

import com.tommo.kademlia.identity.Id

import akka.actor.Actor

class KadActor(id: Id)(implicit config: KadConfig) extends Actor {
  def receive = {
    case _ => 
  }
  
}