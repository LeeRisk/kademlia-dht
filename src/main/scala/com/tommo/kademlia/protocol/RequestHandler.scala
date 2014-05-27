package com.tommo.kademlia.protocol

import akka.actor.{ Actor, ActorRef, Props }
import Message._
import com.tommo.kademlia.store.StoreActor._

class RequestHandler[V](selfNode: ActorRef, kSet: ActorRef, store: ActorRef) extends Actor {

  def receive = {
    case PingRequest => sender ! AckReply
    case kReq: KClosestRequest => kSet forward kReq
    case cacheReq: CacheStoreRequest[V] => store ! cacheReq
    case storeReq: StoreRequest[V] => store ! storeReq
    case FindValueRequest(searchId, k) =>
      val origSender = sender
      context.actorOf(Props(new Actor {
        store ! Get(searchId)
        
        def receive = {
          case GetResult(None) => kSet ! KClosestRequest(searchId, k)
          case kReply: KClosestReply => 
            origSender forward FindValueReply(Left(kReply))
            context.stop(self)
          case GetResult(Some((value, ttl))) =>
            origSender forward FindValueReply(Right(RemoteValue(value, ttl)))
            context.stop(self)
        }
      }))
  }
}

