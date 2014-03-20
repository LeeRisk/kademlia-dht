package com.tommo.kademlia

import akka.testkit.TestFSMRef
import akka.actor.ActorRef
import akka.actor.Actor

import LookupActor._
import com.tommo.kademlia.protocol.ActorNode
import com.tommo.kademlia.identity.Id
import scala.collection.immutable.TreeMap
import scala.concurrent.duration._

class LookupActorTest extends BaseTestKit("LookupSpec") {
  def actor(kBucketActor: ActorRef = testActor)(implicit config: KadConfig) = {
    val ref = TestFSMRef(new LookupActor(kBucketActor)(config))
    (ref.underlyingActor, ref)
  }

  val id = mockZeroId(4)

  def actorNode(id: String) = ActorNode(testActor, Id(id))

  val mockLocalNodes = List(actorNode("1000"), actorNode("1001"), actorNode("1011"), actorNode("1101"))

  val mockQueryNodeData = {
    val seen = TreeMap(mockLocalNodes.map(actorNodeToKeyPair(_)): _*)(new id.Order)
    val query = seen.take(mockConfig.concurrency).toMap
    QueryNodeData(id, query, seen)
  }

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

    ref.stateName should equal(QueryKBucket)
    ref.stateData should equal(QueryKBucketData(id))
  }

  it should "go to QueryNode state when the k-closest node is received from the KBucket" in {
    val (_, ref) = actor()

    ref.setState(QueryKBucket, QueryKBucketData(id))

    ref ! KClosest(mockLocalNodes)

    ref.stateName should equal(QueryNode)
  }

  it should "query α concurrently for each round" in {
    val (_, ref) = actor()

    ref.setState(QueryKBucket, QueryKBucketData(id))
    ref.setState(QueryNode, mockQueryNodeData)

    expectMsgAllOf(GetKClosest(id, mockConfig.kBucketSize), GetKClosest(id, mockConfig.kBucketSize))
  }

  it should "update the QueryState of the node that returned kClosest that was queried in the current round and add the result to seen" in {
    val (_, ref) = actor()

    ref.setState(QueryNode, mockQueryNodeData)

    val (mockId, queryState) = mockQueryNodeData.querying.head

    val mockKResponse = KClosestRemote(ActorNode(queryState.ref, mockId), List(actorNode("0001")))
    ref ! mockKResponse

    val expectedQuerying = mockQueryNodeData.querying + (mockId -> queryState.copy(success = true))
    val expectedSeen = mockQueryNodeData.seen + actorNodeToKeyPair(actorNode("0001"))

    ref.stateData should equal(QueryNodeData(id, expectedQuerying, expectedSeen))
  }

  it should "go to QueryNode -> GatherNode after the specified config timeout" in {
    val (_, ref) = actor()(new TestKadConfig {
      override val roundTimeOut = 500 milliseconds
    })

    ref.setState(QueryNode, mockQueryNodeData)
    
    ref.isStateTimerActive shouldBe true
    
    awaitAssert(ref.stateName should equal(GatherNode), max = 600 millis, interval = 2 millis) // allow some time
  }
  
  it should "" in {
    
  }
}