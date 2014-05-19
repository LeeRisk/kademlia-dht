package com.tommo.kademlia.store

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.BaseFixture
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.util.EventSource._
import com.tommo.kademlia.routing.KBucketSetActor._
import StoreActor._

import org.mockito.Matchers._
import org.mockito.Mockito._

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
    val refreshProbe = TestProbe()

    val verifyRef = TestActorRef[StoreActor[Int]](Props(new StoreActor[Int](id, refreshStore, kBucketProbe.ref, senderProbe.ref, refreshProbe.ref) with MockStore))
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
      val expectedMsgs = toReplicate.map { case (id, value) => NodeRequest(newNode.ref, StoreRequest(mockConfig.id, id, value), false) }

      when(mockStore.findCloserThan(any(), any())).thenReturn(toReplicate)

      verifyRef ! Add(newNode)

      verify(mockStore).findCloserThan(mockConfig.id, Id("1010"))

      senderProbe.expectMsgAllOf(expectedMsgs: _*)
    }
  }

  test("if original publisher republish every refreshStore duration") {
    new Fixture {
      val anInsert = Insert(aRandomId, 1)
      
      verifyRef ! anInsert
      
      refreshProbe.expectMsg(RefreshOriginal(anInsert.key, mockConfig.refreshStore))
    }
  }
  
  test("") {
    
  }

}

/*
support getkClosest
* remove
* 
*/

