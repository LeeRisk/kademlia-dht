//package com.tommo.kademlia.util
//
//import com.tommo.kademlia.BaseTestKit
//import com.tommo.kademlia.protocol.ActorNode
//import com.tommo.kademlia.protocol.Message._
//import com.tommo.kademlia.protocol.RequestSenderActor._
//import com.tommo.kademlia.identity.Id
//import com.tommo.kademlia.routing.KBucketSetActor._
//import com.tommo.kademlia.misc.time.Clock
//import scala.concurrent.duration._
//import akka.actor.{ Props, Actor, ActorRef }
//import akka.testkit.{ TestActorRef, TestProbe, TestActor }
//import org.mockito.Matchers._
//import org.mockito.Mockito._
//
//class TimeRefresherTest extends BaseTestKit("KBucketSpec")  {
//    test("return the KClosest to an id by invoking getClosestInOrder method") {
//        val verifyRef = TestActorRef[BLeh](Props(new BLeh with TimedRefresher[Int] with Clock {
//          override val refreshCycle = 2 seconds
//          override val onRefreshFn = (x: Int) => {3; println("Bleh")}
//        }))
//    }
//    
//}