package com.tommo.kademlia.util

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.actorRef2Scala

trait EventSource { this: Actor =>
  import EventSource._
  
  var listeners = Vector.empty[ActorRef]
  
  def sendEvent[T](event: T): Unit = listeners foreach { _ ! event }
  
  def eventSourceReceive: Receive = {
    case RegisterListener(listener) =>
      listeners = listeners :+ listener
    case UnregisterListener(listener) =>
      listeners = listeners filter { _ != listener }
  }
}

object EventSource {
  case class RegisterListener(listener: ActorRef)
  case class UnregisterListener(listener: ActorRef)
}