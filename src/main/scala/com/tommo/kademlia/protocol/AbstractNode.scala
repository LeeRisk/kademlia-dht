package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id
import akka.actor.ActorRef

sealed abstract class AbstractNode {
  def id: Id
}

case class Node(host: Host, id:Id) extends AbstractNode

case class ActorNode(actor: ActorRef, id:Id) extends AbstractNode

