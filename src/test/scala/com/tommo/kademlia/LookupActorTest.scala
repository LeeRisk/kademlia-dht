package com.tommo.kademlia

import akka.testkit.TestFSMRef
import akka.actor.ActorRef

import LookupActor._

class LookupActorTest extends BaseTestKit("LookupSpec") {
	def actor(kBucketActor: ActorRef = testActor) = {
	  val ref = TestFSMRef(new LookupActor(kBucketActor))
	  (ref.underlyingActor, ref)
	} 
  
	"A LookupActor" should "start in Initial state with Empty data" in {
	  val (lookup, ref) = actor()
	  
	  ref.stateName should equal (LookupActor.Initial)
	  ref.stateData should equal (LookupActor.Empty)
	}
	
	it should "send the kbucketActor the id to get the k-closest locally known nodes" in {
	  val (_, ref) = actor()
	  
	  val id = mockZeroId(10);
	  
	  ref ! id
	  
	  expectMsg(GetKClosest(id, mockConfig.kBucketSize))
	}
	
	
	
	it should "go to QueryKBucketSet state when find k-closest request from id is received" in {
	  val (lookup, ref) = actor()
	  
	  val id = mockZeroId(10);
	  
	  ref ! id
	  
	  ref.stateName should equal (QueryKBucketSet)
	  ref.stateData should equal (QueryKBucketData(id))
	}
}