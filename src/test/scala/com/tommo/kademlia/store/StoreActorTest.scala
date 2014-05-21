package com.tommo.kademlia.store

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.BaseFixture
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.util.EventSource._
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.util.RefreshActor._
import com.tommo.kademlia.lookup.LookupDispatcher.FindKNode
import com.tommo.kademlia.lookup.LookupNode.LookupResult
import com.tommo.kademlia.protocol.ActorNode
import StoreActor._

import org.mockito.Matchers._
import org.mockito.Mockito._

import scala.math.exp
import akka.testkit.{ TestActorRef, TestProbe, TestActor }
import akka.actor.{ Props, Actor, ActorRef }

class StoreActorTest extends BaseTestKit("StoreSpec") with BaseFixture {

  trait Fixture {
    val mockStore = mock[Store[Int]]

    trait MockStore extends Store[Int] {
      def insert(id: Id, v: Int) = mockStore.insert(id, v)
      def get(id: Id) = mockStore.get(id)
      def remove(id: Id) = mockStore.remove(id)
      def findCloserThan(source: Id, target: Id) = mockStore.findCloserThan(source, target)
    }

    import mockConfig._

    val kBucketProbe = TestProbe()
    val senderProbe = TestProbe()
    val lookupProbe = TestProbe()
    val refreshProbe = TestProbe()

    val verifyRef = TestActorRef[StoreActor[Int]](Props(new StoreActor[Int](id, kBucketProbe.ref, senderProbe.ref, refreshProbe.ref, lookupProbe.ref) with MockStore))
  }

  test("invoke insert when Insert msg received") {
    new Fixture {
      val (expectedId, expectedValue) = (aRandomId, 1)
      verifyRef ! Insert(expectedId, expectedValue)

      verify(mockStore).insert(expectedId, expectedValue)
    }
  }

  test("registers self as listener for kBucket actor") {
    new Fixture {
      kBucketProbe.expectMsg(RegisterListener(verifyRef))
    }
  }

  test("if a new node is encountered that is closer to any of stored values; replicate to this new node") {
    new Fixture {
      val newNode = mockActorNode("1010")
      val toReplicate = List((Id("1111"), 1), (Id("1110"), 2))
      val expectedMsgs = toReplicate.map { case (key, value) => NodeRequest(newNode.ref, StoreRequest(id, key, value), false) }

      when(mockStore.findCloserThan(any(), any())).thenReturn(toReplicate)

      verifyRef ! Add(newNode)

      verify(mockStore).findCloserThan(id, Id("1010"))

      senderProbe.expectMsgAllOf(expectedMsgs: _*)
    }
  }

  test("if original publisher lookup kclosest to store") {
    new Fixture {
      val anInsert = Insert(aRandomId, 1)
      when(mockStore.get(anInsert.key)).thenReturn(Some(1))
      verifyRef ! anInsert

      lookupProbe.expectMsg(FindKNode(anInsert.key))
    }
  }
  
  test("start republish value for original publisher") {
    new Fixture {
    	val key = aRandomId
    	
    	when(mockStore.get(any())).thenReturn(Some(1))
    	
    	verifyRef ! LookupResult(key, Nil)
    	
    	refreshProbe.expectMsg(Republish(key, mockConfig.refreshStore))
    }
  }

  test("when kclosest received send a StoreRequest") {
    new Fixture {
      val kclosest = ActorNode(TestProbe().ref, aRandomId) :: ActorNode(TestProbe().ref, aRandomId) :: Nil

      val result = LookupResult(aRandomId, kclosest)
      when(mockStore.get(result.searchId)).thenReturn(Some(1))
      
      val toStore = kclosest.map(n => NodeRequest(n.ref, StoreRequest(id, result.searchId, StoreRequest(id, result.searchId, 1))))

      verifyRef ! result
      senderProbe.expectMsgAllOf(toStore: _*)
    }

  }

  test("invoke Insert when StoreRequest received") {
    new Fixture {
      val storeReq = StoreRequest(Id("1010"), Id("1111"), 2)
      verifyRef ! storeReq
      verify(mockStore).insert(storeReq.key, storeReq.value)
    }
  }

  test("get nodes in between for StoreRequest") {
    new Fixture {
      val storeReq = StoreRequest(Id("1010"), Id("1111"), 2)
      verifyRef ! storeReq
      kBucketProbe.fishForMessage() {
        case GetNumNodesInBetween(id) => true
        case _ => false
      }
    }
  }

  test("set refresh timer to republish inversely exponentially proportional to # of intermediate nodes") {
    new Fixture {

      val numBetween = 30
      val expectExpiration = mockConfig.refreshStore * (1 / exp(numBetween.toDouble / mockConfig.kBucketSize))

      verifyRef ! NumNodesInBetween(Id("1010"), numBetween)

      refreshProbe.expectMsg(ExpireRemoteStore(Id("1010"), expectExpiration, ExpireValue(0)))
    }
  }

  test("if ExpireValue event received - remove from store") {
    new Fixture {
      verifyRef ! NumNodesInBetween(Id("1010"), 0)
      verifyRef ! RefreshDone(Id("1010"), ExpireValue(0))
      verify(mockStore).remove(Id("1010"))
    }
  }
  
  test("if RepublishValue event received - look up kclosest to store") {
    new Fixture {
      val key = aRandomId
      verifyRef ! RefreshDone(key, RepublishValue)
      
      lookupProbe.expectMsg(FindKNode(key))
      
    }
  }
}
