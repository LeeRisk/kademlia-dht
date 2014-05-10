package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id
import akka.actor.ActorRef

abstract class Message {
  val sender: Id
}

private[protocol] sealed class MessageWrapper(msg: Message) extends Message {
  val sender = msg.sender
}

trait Request extends Message 
trait MutableRequest extends Request
trait Reply extends Message

/* Piggyback on request/response to verify authenticity of sender/receiver */
sealed trait AuthChallenge extends Message { val toEcho: Int }
sealed trait AuthReply extends Message { val echoId: Int }

object Message {
  case class PingRequest(val sender: Id) extends Request

  case class KClosestRequest(val sender: Id, searchId: Id, k: Int) extends Request
  case class KClosestReply(val sender: Id, nodes: List[ActorNode]) extends Reply

  case class StoreRequest[V](val sender: Id, val key: Id, value: V) extends MutableRequest
  case class AckReply(val sender: Id) extends Reply 

  case class FindValueRequest(val sender: Id, val key: Id) extends Request
  case class FindValueReply[V](val sender: Id, result: Either[List[ActorNode], Set[V]]) extends Reply

  case class AuthSenderRequest(req: Request, val toEcho: Int) extends MessageWrapper(req) with AuthChallenge
  case class AuthRecieverReply(reply: Reply, echoId: Int, toEcho: Int) extends MessageWrapper(reply) with AuthReply with AuthChallenge
  case class AuthSenderReply(sender: Id, echoId: Int) extends AuthReply

}

