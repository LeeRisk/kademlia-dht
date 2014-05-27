package com.tommo.kademlia.store

import scala.concurrent.duration._
import scala.collection.mutable.Map
import akka.actor.{ Actor, ActorRef, Props }

import com.tommo.kademlia.misc.time.{ Clock, Epoch }
import com.tommo.kademlia.util.EventSource._
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.RequestDispatcher._
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.util.RefreshActor._
import com.tommo.kademlia.KadConfig
import com.tommo.kademlia.lookup._

object StoreActor {

  trait Provider[V] {
    def storeActor(selfNode: ActorNode, kBucketRef: ActorRef, timerRef: ActorRef, lookupNodeDispatcher: ActorRef)(implicit config: KadConfig): Actor =
      new StoreActor[V](selfNode, kBucketRef, timerRef, lookupNodeDispatcher) with Clock with InMemoryStore[V]
  }

  private[store] case class InsertMetaData(generation: Int, currTime: Epoch, ttl: FiniteDuration, originalPublish: Boolean)

  private[store] case class NumNodeBetweenGen(key: Id, numNodes: Int, gen: Int)

  case class Insert[V](key: Id, value: V)
  case class Get(key: Id)
  case class GetResult[V](value: Option[(V, FiniteDuration)])

  case class Republish(val key: Id, val after: Duration, val value: RepublishValue, val refreshKey: String = "republishStore") extends Refresh
  case class RepublishValue(generation: Int)

  case class RepublishRemote(val key: Id, val after: Duration, val value: RepublishRemoteValue, val refreshKey: String = "republishRemoteStore") extends Refresh
  case class RepublishRemoteValue(generation: Int)

  case class ExpireRemoteStore(val key: Id, val after: Duration, val value: ExpireValue, val refreshKey: String = "expireRemoteStore") extends Refresh
  case class ExpireValue(generation: Int)
}

class StoreActor[V](selfNode: ActorNode, kBucketRef: ActorRef, timerRef: ActorRef, lookupNodeDispatcher: ActorRef)(implicit val config: KadConfig) extends Actor {
  this: Store[V] with Clock =>

  import StoreActor._
  import config._

  override def preStart() {
    kBucketRef ! RegisterListener(self)
  }

  override def postStop() {
    kBucketRef ! UnregisterListener(self)
  }

  val metaMap = Map[Id, InsertMetaData]() // invariant - if a key exists in the underlying store then it also exists in this map

  def receive = {
    case insertMsg: Insert[V] =>
      import insertMsg._

      updateAndDo(key, expireRemote, true) {
        meta =>
          insert(key, value)
          lookupNodeDispatcher ! LookupNodeFSM.FindKClosest(key)
          timerRef ! Republish(key, republishOriginal, RepublishValue(meta.generation))
      }
      
    case Get(key) => 
      get(key) match {
        case Some(aValue) => sender ! executeFn(key, GetResult[V](None)) {
          meta => GetResult(Some((aValue, getCurrTTL(meta))))
        }
        
        case _ => sender ! GetResult(None)
      }

    case LookupNodeFSM.Result(key, nodes) =>
      get(key) match { // if kclosest returned after an expiration timer executed then don't do anything
        case Some(toStore) => executeFn(key, ()) {
          meta => nodes.map(n => NodeRequest(n.ref, StoreRequest(key, RemoteValue(toStore, getCurrTTL(meta)), meta.generation))).foreach(selfNode.ref ! _)
        }
        case _ =>
      }

    case Add(newNode) => // listener event from kbucket that signals a new node was added
      findCloserThan(selfNode.id, newNode.id).flatMap {
        case (key, value) =>
          executeFn[Option[NodeRequest]](key, None)(meta => Some(NodeRequest(newNode.ref, StoreRequest(key, RemoteValue(value, getCurrTTL(meta)), meta.generation), false)))
      }.foreach(selfNode.ref ! _)

    case storeReq: StoreRequest[V] =>
      import storeReq._

      updateAndDo(key, expireRemote, false, storeReq.generation) {
        meta =>
          insert(key, toStore.value)
          timerRef ! RepublishRemote(key, republishRemote, RepublishRemoteValue(meta.generation))
          scheduleExpire(key, meta.ttl, meta.generation)
      }

    case cacheReq: CacheStoreRequest[V] =>
      import cacheReq._
      updateAndDo(key, toStore.ttl, false) {
        meta =>
          insert(key, toStore.value)
          val thisSender = sender

          context.actorOf(Props(new Actor() {
            kBucketRef ! GetNumNodesInBetween(key)

            def receive = {
              case NumNodesInBetween(numNode) =>
                thisSender ! NumNodeBetweenGen(key, numNode, meta.generation)
                context.stop(self)
            }
          }))
      }

    case NumNodeBetweenGen(key, numNode, gen) =>
      doIfGenSameOrHigher(key, gen) {
        meta =>
          val expInverse = meta.ttl * (1 / scala.math.exp(numNode.toDouble / kBucketSize))
          timerRef ! ExpireRemoteStore(key, expInverse, ExpireValue(meta.generation))
      }

    case RefreshDone(key: Id, ExpireValue(gen)) =>
      doIfGenSameOrHigher(key, gen) {
        case meta if !meta.originalPublish =>
          metaMap -= key
          remove(key)
      }

    case RefreshDone(key: Id, RepublishRemoteValue(gen)) =>
      doIfGenSameOrHigher(key, gen) {
        meta => timerRef ! RepublishRemote(key, republishRemote, RepublishRemoteValue(meta.generation))
      }

    case RefreshDone(key: Id, RepublishValue(gen)) =>
      updateAndDo(key, expireRemote, true, generation = gen + 1) {
        meta =>
          lookupNodeDispatcher ! LookupNodeFSM.FindKClosest(key)
          timerRef ! Republish(key, republishOriginal, RepublishValue(meta.generation))
      }
  }

  private def getCurrTTL(meta: InsertMetaData) = (meta.currTime + meta.ttl.toMillis - getTime()) match {
    case ttl if ttl > 0 => ttl milliseconds
    case _ => 0 seconds
  }

  private def scheduleExpire(key: Id, at: FiniteDuration, gen: Int) = timerRef ! ExpireRemoteStore(key, at, ExpireValue(gen))

  private def doIfGenSameOrHigher(key: Id, gen: Int)(fn: InsertMetaData => Unit) = executeFn(key, ()) { meta => if (gen >= meta.generation) fn(meta) }

  private def updateAndDo(key: Id, ttl: FiniteDuration, originalPublish: Boolean, generation: Int = -1)(fn: InsertMetaData => Unit) = {
    def isOriginal(meta: InsertMetaData) = if (originalPublish || meta.originalPublish) true else false

    def addAndReturn(generation: Int, originalPublish: Boolean): Option[InsertMetaData] = {
      val meta = InsertMetaData(generation, getTime(), ttl, originalPublish)
      metaMap += key -> meta
      Some(meta)
    }

    val updated = executeFn(key, addAndReturn(0, originalPublish)) {
      case meta if generation == -1 && isOriginal(meta) => addAndReturn(meta.generation + 1, true)
      case meta if generation >= meta.generation => addAndReturn(generation, isOriginal(meta))
      case _ => None
    }

    updated match {
      case Some(x) => fn(x)
      case _ =>
    }
  }

  private def executeFn[T](key: Id, noExist: => T)(exist: InsertMetaData => T) = {
    metaMap.get(key) match {
      case Some(meta) => exist(meta)
      case _ => noExist
    }
  }
}
