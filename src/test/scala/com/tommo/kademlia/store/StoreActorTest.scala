package com.tommo.kademlia.store

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.BaseFixture
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.protocol.RequestDispatcher._
import com.tommo.kademlia.util.EventSource._
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.util.RefreshActor._
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.misc.time.Clock
import com.tommo.kademlia.lookup._
import StoreActor._

import org.mockito.Matchers._
import org.mockito.Mockito._

import scala.math.exp
import scala.concurrent.duration._
import akka.testkit.{ TestActorRef, TestProbe, TestActor }
import akka.actor.{ Props, Actor, ActorRef }

class StoreActorTest extends BaseTestKit("StoreSpec") with BaseFixture {
  trait Fixture {
    val mockStore = mock[Store[Int]]
    val mockClock = mock[Clock]

    when(mockClock.getTime()).thenReturn(0) // make clock value a known value

    trait MockStoreClock extends Store[Int] with Clock {
      def insert(id: Id, v: Int) = mockStore.insert(id, v)
      def get(id: Id) = mockStore.get(id)
      def remove(id: Id) = mockStore.remove(id)
      def findCloserThan(source: Id, target: Id) = mockStore.findCloserThan(source, target)
      override def getTime() = mockClock.getTime()
    }

    import mockConfig._

    val kBucketProbe = TestProbe()
    val senderProbe = TestProbe()
    val lookupProbe = TestProbe()
    val refreshProbe = TestProbe()

    def insertKey(keyPair: (Id, Int)*) = keyPair.foreach(kp => verifyRef ! Insert(kp._1, kp._2))

    def advanceClockBy(ms: Long) = when(mockClock.getTime()).thenReturn(ms)

    val verifyRef = TestActorRef[StoreActor[Int]](Props(new StoreActor[Int](id, kBucketProbe.ref, senderProbe.ref, refreshProbe.ref, lookupProbe.ref) with MockStoreClock))

    val aTTL = 10 seconds
  }

  test("invoke insert when Insert msg received") {
    new Fixture {
      val keyPair = (aRandomId, 1)
      verifyRef ! insertKey(keyPair)

      verify(mockStore).insert(keyPair._1, keyPair._2)
    }
  }

  test("start republish value for original publisher") {
    new Fixture {
      val keyPair = (aRandomId, 1)
      verifyRef ! insertKey(keyPair)

      refreshProbe.expectMsg(Republish(keyPair._1, mockConfig.republishOriginal, RepublishValue(0))) // the first generation
    }
  }

  test("registers self as listener for kBucket actor") {
    new Fixture {
      kBucketProbe.expectMsg(RegisterListener(verifyRef))
    }
  }

  test("if a new node is encountered that is closer to any of stored values; replicate to this new node") {
    new Fixture {
      val replicateOne = (Id("1111"), 3)
      val duplicateOne = (replicateOne._1, 1)
      val replicateTwo = (Id("1110"), 2)

      insertKey(replicateOne, duplicateOne, replicateTwo)

      advanceClockBy(10)
      val ttl = mockConfig.expireRemote - (10 milliseconds)

      val closerNode = mockActorNode("1010")

      when(mockStore.findCloserThan(any(), any())).thenReturn(replicateOne :: replicateTwo :: Nil)

      verifyRef ! Add(closerNode)

      verify(mockStore).findCloserThan(id, Id("1010"))

      senderProbe.expectMsgAllOf(NodeRequest(closerNode.ref, StoreRequest(duplicateOne._1, RemoteValue(replicateOne._2, ttl), 1), false),
        NodeRequest(closerNode.ref, StoreRequest(replicateTwo._1, RemoteValue(replicateTwo._2, ttl), 0), false))
    }
  }

  test("if original publisher lookup kclosest to store") {
    new Fixture {
      val anInsert = Insert(aRandomId, 1)
      when(mockStore.get(anInsert.key)).thenReturn(Some(1))
      verifyRef ! anInsert

      lookupProbe.expectMsg(LookupNode.FindKClosest(anInsert.key))
    }
  }

  test("when kclosest received send a StoreRequest") {
    new Fixture {
      val kclosest = ActorNode(TestProbe().ref, aRandomId) :: ActorNode(TestProbe().ref, aRandomId) :: Nil

      val keyPair = (aRandomId, 1)
      verifyRef ! insertKey(keyPair)

      val result = LookupNode.Result(keyPair._1, kclosest)
      when(mockStore.get(result.searchId)).thenReturn(Some(1))

      advanceClockBy(10)
      val ttl = mockConfig.expireRemote - (10 milliseconds)

      verifyRef ! result
      senderProbe.expectMsgAllOf(kclosest.map(n => NodeRequest(n.ref, StoreRequest(result.searchId, RemoteValue(1, ttl), 0))): _*)
    }
  }

  test("invoke Insert when StoreRequest received") {
    new Fixture {
      val storeReq = StoreRequest(Id("1111"), RemoteValue(1, aTTL), 0)
      verifyRef ! storeReq
      verify(mockStore).insert(storeReq.key, storeReq.toStore.value)
    }
  }
  test("start republish remote for StoreRequest") {
    new Fixture {
      verifyRef ! StoreRequest(Id("1111"), RemoteValue(1, aTTL), 0)
      refreshProbe.fishForMessage() {
        case RepublishRemote(Id("1111"), mockConfig.republishRemote, RepublishRemoteValue(0), _) => true
        case _ => false
      }
    }
  }

  test("schedule remote store expiration based on ttl") {
    new Fixture {
      val expectTTL = 10 milliseconds

      verifyRef ! StoreRequest(Id("1111"), RemoteValue(1, expectTTL), 0)

      refreshProbe.fishForMessage() {
        case ExpireRemoteStore(Id("1111"), expectTTL, ExpireValue(0), _) => true
        case _ => false
      }
    }
  }

  test("invoke insert when CacheStoreRequest received") {
    new Fixture {
      val cacheReq = CacheStoreRequest(Id("1111"), RemoteValue(2, aTTL))

      verifyRef ! cacheReq

      verify(mockStore).insert(cacheReq.key, cacheReq.toStore.value)
    }
  }

  test("get nodes in between for CacheStoreRequest") {
    new Fixture {
      val cacheReq = CacheStoreRequest(Id("1111"), RemoteValue(2, aTTL))
      verifyRef ! cacheReq
      kBucketProbe.fishForMessage() {
        case GetNumNodesInBetween(cacheReq.key) => true
        case _ => false
      }
    }
  }

  test("set expire timer to inversely exponentially proportional to # of intermediate nodes") {
    new Fixture {
      val ttl = 10 hours

      val numBetween = 30
      val expectExpiration = ttl * (1 / exp(numBetween.toDouble / mockConfig.kBucketSize))

      val cacheReq = CacheStoreRequest(Id("1111"), RemoteValue(2, ttl))

      verifyRef ! cacheReq
      verifyRef ! NumNodeBetweenGen(cacheReq.key, numBetween, 0)

      refreshProbe.expectMsg(ExpireRemoteStore(cacheReq.key, expectExpiration, ExpireValue(0)))
    }
  }

  test("if ExpireValue event received - remove from store") {
    new Fixture {
      val storeReq = StoreRequest(Id("1111"), RemoteValue(1, aTTL), 0)
      verifyRef ! storeReq
      verifyRef ! RefreshDone(storeReq.key, ExpireValue(0))
      verify(mockStore).remove(storeReq.key)
    }
  }

  test("if RepublishValue event received - look up kclosest to store and set publish original timer") {
    new Fixture {
      val keyPair = (aRandomId, 1)

      verifyRef ! insertKey(keyPair)

      verifyRef ! RefreshDone(keyPair._1, RepublishValue(0))

      lookupProbe.receiveN(2)

      refreshProbe.fishForMessage() {
        case Republish(keyPair._1, mockConfig.republishOriginal, RepublishValue(1), _) => true
        case _ => false
      }

    }
  }

}
