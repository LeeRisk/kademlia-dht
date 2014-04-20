package com.tommo.kademlia.routing

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.protocol.Message._
import com.tommo.kademlia.protocol.RequestSenderActor._
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.KBucketSetActor._

import akka.actor.{ Props, Actor, ActorRef }
import akka.testkit.{ TestActorRef, TestProbe, TestActor }
import org.mockito.Matchers._
import org.mockito.Mockito._

class KBucketSetActorTest extends BaseTestKit("KBucketSpec") with BaseKBucketFixture {

  trait Fixture extends BaseKBucketFixture {
    val mockProbe = TestProbe()
    val reqSendProbe = TestProbe()

    val kSet = mock[KBucketSet[ActorNode]]

    val kClosest = ActorNode(mockProbe.ref, aRandomId) :: ActorNode(mockProbe.ref, aRandomId) :: Nil

    when(kSet.getClosestInOrder(anyInt(), any())).thenReturn(kClosest)

    val verifyRef = TestActorRef[KBucketSetActor](Props(new KBucketSetActor(kSet, reqSendProbe.ref)))

  }

  test("return the KClosest to an id by invoking getClosestInOrder method") {
    new Fixture {
      verifyRef ! GetKClosest(mockZeroId(4), mockConfig.kBucketSize)

      expectMsg(KClosest(mockZeroId(4), kClosest))

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
      
      when(kSet.isFull(any())).thenReturn(true)
      when(kSet.getLowestOrder(any())).thenReturn(lowest)
      
      reqSendProbe.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = msg match {
          case NodeRequest(lowest.ref, PingRequest(lowest.id)) => sender ! PingReply(lowest.id); TestActor.NoAutoPilot
          case _ => TestActor.NoAutoPilot
        }
      })
      
      verifyRef ! Add(ActorNode(mockProbe.ref, aRandomId))
      
      reqSendProbe.expectMsg(NodeRequest(lowest.ref, PingRequest(lowest.id)))
      
      verify(kSet, never()).add(any())
    }
  }
  
  test("add if kBucket is full and the lowest ordered node is dead") { 
    new Fixture {
      val lowest = ActorNode(mockProbe.ref, aRandomId)
      
      when(kSet.isFull(any())).thenReturn(true)
      when(kSet.contains(lowest)).thenReturn(true)
      when(kSet.getLowestOrder(any())).thenReturn(lowest)
      
      reqSendProbe.setAutoPilot(new TestActor.AutoPilot {
        def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = msg match {
          case NodeRequest(lowest.ref, PingRequest(lowest.id)) => Thread.sleep(mockConfig.roundTimeOut.toMillis + 10); TestActor.NoAutoPilot
          case _ => TestActor.NoAutoPilot
        }
      })
      
      val toAdd = ActorNode(mockProbe.ref, aRandomId)
      
      verifyRef ! Add(toAdd)
      
      reqSendProbe.expectMsg(NodeRequest(lowest.ref, PingRequest(lowest.id)))
      
      awaitAssert(verify(kSet).add(toAdd))
    }
  }
}