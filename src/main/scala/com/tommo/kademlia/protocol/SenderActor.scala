package com.tommo.kademlia.protocol

import akka.actor.{Actor, ActorRef}

class SenderActor(kBucketActor: ActorRef) extends Actor {
  this: AuthActor.Provider =>

  def receive = {
    case req: Request => 
  }
}