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

  implicit val order = new id.Order

  def actorNode(id: String) = ActorNode(testActor, Id(id))

  def actorNodePair(id: String, queried: Boolean = false, round: Int = 1) = actorNodeToKeyPair(actorNode(id), queried, round);

  val mockLocalNodes = List(actorNode("1000"), actorNode("1001"), actorNode("1011"), actorNode("1101")) // must be ordered with mockZeroId

  def withSuccess(mockNodes: List[ActorNode], numQuery: Int = mockConfig.concurrency)(qFn: Id => Boolean) = {
    val seen = TreeMap(mockLocalNodes.map(x => actorNodeToKeyPair(x)): _*)(new id.Order)

    val query = seen.takeRight(numQuery).map(Function.tupled((id, nodeQuery) => (id, nodeQuery.copy(queried = qFn(id)))))
    QueryNodeData(id, query, seen)
  }

  val initQueryState = withSuccess(mockLocalNodes)(x => false)

  "A LookupActor" should "start in Initial state with Empty data" in {
    val (lookup, ref) = actor()

    ref.stateName should equal(LookupActor.Initial)
    ref.stateData should equal(LookupActor.Empty)
  }

  it should "send to the kbucketActor the id to get the k-closest locally known nodes" in {
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
    ref.setState(QueryNode, initQueryState)

    expectMsgAllOf(GetKClosest(id, mockConfig.kBucketSize), GetKClosest(id, mockConfig.kBucketSize))
  }

  it should "update the QueryState of the node that returned kClosest that was queried in the current round and add the result to seen" in {
    val (_, ref) = actor()

    val queryNodeState = initQueryState

    ref.setState(QueryNode, queryNodeState)

    val (mockId, queryState) = queryNodeState.querying.head

    val mockKResponse = KClosestRemote(ActorNode(queryState.ref, mockId), List(actorNode("0001")))
    ref ! mockKResponse

    val expectedQuerying = queryNodeState.querying + (mockId -> queryState.copy(queried = true))
    val expectedSeen = queryNodeState.seen + actorNodeToKeyPair(actorNode("0001"), round = 2)

    ref.stateData should equal(QueryNodeData(id, expectedQuerying, expectedSeen))
  }

  it should "go to QueryNode -> GatherNode after the specified config timeout" in {
    val (_, ref) = actor()(new TestKadConfig {
      override val roundTimeOut = 500 milliseconds
    })

    val queryNodeState = initQueryState
    

    ref.setState(QueryKBucket, QueryKBucketData(id))
    ref.setState(QueryNode, queryNodeState)
    
    awaitAssert(ref.stateName should equal(GatherNode), max = 600 millis, interval = 100 micro) // allow some time

    ref.stateData should equal(queryNodeState.copy(responseCount = queryNodeState.querying.size))
  }

  it should "update all successfully queried nodes in seen" in {
    val (_, ref) = actor()

    val first = mockLocalNodes.take(1)
    val mockStateData = withSuccess(mockLocalNodes)(x => first.exists(_.id == x))

    ref.setState(GatherNode, mockStateData)
    ref ! InitiateGather

    val expectedSeen = mockStateData.seen ++ mockStateData.querying

    inside(ref.stateData) { case QueryNodeData(_, _, seen, _, _) => seen shouldBe expectedSeen }
  }

  it should "continue querying up to α unqueried nodes that is newly discovered among the k-closest" in {
    val (_, ref) = actor()

    val (c1Queried, c2PrevRound, c3) = (actorNodePair("0001", true), actorNodePair("0010"), actorNodePair("0011", round = 3))

    val data = QueryNodeData(id, seen =  TreeMap(c1Queried, c2PrevRound, c3), responseCount = 300, currRound = 2)

    ref.setState(GatherNode, data)
    ref ! InitiateGather

    ref.stateName shouldBe QueryNode
    
    inside(ref.stateData) {
      case QueryNodeData(_, querying, _, responseCount, nextRound) =>
        querying shouldBe Map(c3)
        responseCount shouldBe 0
        nextRound shouldBe 3
    }
  }
  
  it should "query nodes that failed to respond in previous round in addition to the newly discovered" in {
    
  }
  
  
}