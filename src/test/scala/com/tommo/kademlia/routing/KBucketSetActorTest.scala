package com.tommo.kademlia.routing

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.protocol.RequestDispatcher._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.KBucketSetActor._
import com.tommo.kademlia.protocol.Node
import akka.actor.{ Props, Actor, ActorRef }
import akka.testkit.{ TestActorRef, TestProbe, TestActor }
import org.mockito.Matchers._
import org.mockito.Mockito._

class KBucketSetActorTest extends BaseTestKit("KBucketSpec") with BaseKBucketFixture {

  trait Fixture extends BaseKBucketFixture {

    val kSet = mock[KBucketSet[ActorNode]]

    trait MockProvider extends KBucketSet.Provider {
      override def newKSet[T <: Node](id: Id, kBucketCapacity: Int) = kSet.asInstanceOf[KBucketSet[T]]
    }

    val mockProbe = TestProbe()
    val selfProbe = TestProbe()

    val kClosest = ActorNode(mockProbe.ref, aRandomId) :: ActorNode(mockProbe.ref, aRandomId) :: Nil

    when(kSet.getClosestInOrder(anyInt(), any())).thenReturn(kClosest)

    val verifyRef = TestActorRef[KBucketSetActor](Props(new KBucketSetActor(ActorNode(selfProbe.ref, id)) with MockProvider))

  }

  test("return the KClosest to an id by invoking getClosestInOrder method") {
    new Fixture {
      verifyRef ! KClosestRequest(mockZeroId(4), mockConfig.kBucketSize)

      expectMsg(KClosestReply(id, kClosest))

      verify(kSet).getClosestInOrder(mockConfig.kBucketSize, mockZeroId(4))
    }
  }

  test("add node if the kBucket is not full") {
    new Fixture {
      val actorNode = ActorNode(mockProbe.ref, aRandomId)
      verifyRef ! Add(actorNode)
      verify(kSet).add(actorNode)
    }
  }

  test("don't add if kBucket is full and the lowest ordered node is still alive") {
    new Fixture {
      val lowest = ActorNode(mockProbe.ref, aRandomId)
      val toAdd = ActorNode(mockProbe.ref, aRandomId)

      when(kSet.isFull(any())).thenReturn(true)
      when(kSet.getLowestOrder(any())).thenReturn(lowest)

      selfProbe.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = msg match {
          case NodeRequest(lowest.ref, PingRequest, _, _) =>
            sender ! AckReply; TestActor.NoAutoPilot
          case _ => TestActor.NoAutoPilot
        }
      })

      verifyRef ! Add(toAdd)

      selfProbe.expectMsg(NodeRequest(lowest.ref, PingRequest, customData = (lowest, toAdd)))

      verify(kSet, never()).add(any())
    }
  }

  test("add if kBucket is full and the lowest ordered node is dead") {
    new Fixture {
      val lowest = ActorNode(mockProbe.ref, aRandomId)

      when(kSet.isFull(any())).thenReturn(true)
      when(kSet.contains(lowest)).thenReturn(true)
      when(kSet.getLowestOrder(any())).thenReturn(lowest)

      selfProbe.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = msg match {
          case NodeRequest(lowest.ref, PingRequest, _, _) =>
            sender ! RequestTimeout(PingRequest, customData = (lowest, toAdd)); 
            TestActor.NoAutoPilot
          case _ => TestActor.NoAutoPilot
        }
      })

      val toAdd = ActorNode(mockProbe.ref, aRandomId)

      verifyRef ! Add(toAdd)

      selfProbe.expectMsg(NodeRequest(lowest.ref, PingRequest,  customData = (lowest, toAdd)))

      awaitAssert(verify(kSet).add(toAdd))
    }
  }

  test("return number of kBuckets") {
    new Fixture {
      when(kSet.addressSize).thenReturn(5)

      verifyRef ! GetNumKBuckets

      expectMsg(NumKBuckets(5))

    }
  }
  
  test("get node in between") {
    new Fixture {
      val anId = Id("1010")
      when(kSet.getNodesBetween(anId)).thenReturn(2)
      
      verifyRef ! GetNumNodesInBetween(anId)
      expectMsg(NumNodesInBetween(2))
      verify(kSet).getNodesBetween(anId)
      
    }
  }

  test("discard to add id if it is same as self") {
    new Fixture {
      verifyRef ! Add(ActorNode(testActor, id))
      verifyZeroInteractions(kSet) 
    }
  }
}