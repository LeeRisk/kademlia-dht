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
      def remove(id: Id, v: Int) = mockStore.remove(id, v)
      def getCloserThan(source: Id, target: Id) = mockStore.getCloserThan(source, target)
    }

    val selfId = mockZeroId(4)

    val kBucketProbe = TestProbe()
    val senderProbe = TestProbe()

    val verifyRef = TestActorRef[StoreActor[Int]](Props(new StoreActor[Int](selfId, kBucketProbe.ref, senderProbe.ref) with MockStore))
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
      val toReplicate = Map(Id("1111") -> Set(1), Id("1110") -> Set(2, 3))
      val expectedMsgs = toReplicate.map{case (id, values) => values.map(value => NodeRequest(newNode.ref, StoreRequest(selfId, id, value), false))}.flatten.toSeq
      
      when(mockStore.getCloserThan(any(), any())).thenReturn(toReplicate)

      verifyRef ! Add(newNode)

      verify(mockStore).getCloserThan(selfId, Id("1010"))
      
      senderProbe.expectMsgAllOf(expectedMsgs: _*)
    }
  }
  
  
  test("replublish all stored values every x seconds") {
    
  }

}

/*

timer republish

support getkClosest
* remove
* 
*/

