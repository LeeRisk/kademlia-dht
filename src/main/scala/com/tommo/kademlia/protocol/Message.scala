package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id
import akka.actor.ActorRef
import scala.concurrent.duration.FiniteDuration

abstract class Message {}

trait Request extends Message
trait MutableRequest extends Request
trait Reply extends Message

/* Piggyback on request/response to verify authenticity of sender/receiver */
sealed trait AuthChallenge { val toEcho: Int }
sealed trait AuthReply { val sender: Id; val echoId: Int }

object Message {
  case object PingRequest extends Request

  case class KClosestRequest(searchId: Id, k: Int) extends Request
  case class KClosestReply(val from: Id, nodes: List[ActorNode]) extends Reply

  case class FindValueRequest(val searchId: Id, k: Int) extends Request
  case class FindValueReply[V](val result: Either[KClosestReply, RemoteValue[V]]) extends Reply

  case class CacheStoreRequest[V](val key: Id, toStore: RemoteValue[V]) extends MutableRequest
  case class StoreRequest[V](val key: Id, toStore: RemoteValue[V], generation: Int) extends MutableRequest
  case object AckReply extends Reply

  case class RemoteValue[V](value: V, ttl: FiniteDuration)

  case class AuthReceiverRequest(req: Request, val toEcho: Int) extends AuthChallenge
  case class AuthRecieverReply(reply: Reply, echoId: Int, toEcho: Int, val sender: Id) extends AuthReply with AuthChallenge
  case class AuthSenderReply(sender: Id, echoId: Int) extends AuthReply
}

