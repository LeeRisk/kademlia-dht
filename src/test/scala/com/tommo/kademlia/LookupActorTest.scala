package com.tommo.kademlia

import akka.testkit.TestFSMRef
import akka.actor.ActorRef
import akka.actor.Actor

import LookupActor._
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id
import scala.collection.immutable.TreeMap

class LookupActorTest extends BaseTestKit("LookupSpec") {
  def actor(kBucketActor: ActorRef = testActor) = {
    val ref = TestFSMRef(new LookupActor(kBucketActor))
    (ref.underlyingActor, ref)
  }

  val id = mockZeroId(4)
  
  def getKeyPair(actorNode: ActorNode) = (actorNode.id, actorNode)
  
  val mockNodes = null
//  val mockNodes = TreeMap(getKeyPair(ActorNode(testActor, Id("0000"))))
//      (Id("0000"), ActorNode(testActor, Id("0000"))), 
//      (Id("0001"), ActorNode(testActor, Id("0001")), ActorNode(testActor, Id("0010")))

  "A LookupActor" should "start in Initial state with Empty data" in {
    val (lookup, ref) = actor()

    ref.stateName should equal(LookupActor.Initial)
    ref.stateData should equal(LookupActor.Empty)
  }

  it should "send the kbucketActor the id to get the k-closest locally known nodes" in {
    val (_, ref) = actor()

    ref ! id

    expectMsg(GetKClosest(id, mockConfig.kBucketSize))
  }

  it should "go to QueryKBucketSet state when find k-closest request from id is received" in {
    val (lookup, ref) = actor()

    ref ! id

    ref.stateName should equal(QueryKBucketSet)
    ref.stateData should equal(QueryKBucketSetData(id))
  }

  it should "go to QueryNode state when the k-closest node is received from the KBucket" in {
    val (_, ref) = actor()

    ref.setState(QueryKBucketSet, QueryKBucketSetData(id))

    ref ! KClosest(mockNodes)

    ref.stateName should equal(QueryNode)
    ref.stateData should equal(QueryNodeData(id, mockNodes))

  }

  it should "query α nodes at the same time" in {
    val (_, ref) = actor()
    
    ref.setState(QueryKBucketSet, QueryKBucketSetData(id))
    ref.setState(QueryNode, QueryNodeData(id, mockNodes))
    
    expectMsgAllOf(GetKClosest(id, mockConfig.kBucketSize), GetKClosest(id, mockConfig.kBucketSize))
  }

}