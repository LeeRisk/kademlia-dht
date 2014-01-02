package com.tommo.kademlia.routing

import scala.collection.immutable.TreeSet

import com.tommo.kademlia.BaseUnitTest
import com.tommo.kademlia.identity.Id

import com.tommo.kademlia.protocol.Node
import com.tommo.kademlia.protocol.Host

class LastSeenOrderingTest extends BaseUnitTest {
  
  implicit val ordering = LastSeenOrdering()
  
  "A LastSeenOrdering" should "be ordered ascending by time" in {
    val someNode = Node(Host("hostname:9009"), Id("anyString".getBytes()))

    val first = TimeStampNode(someNode, time =  1)
    val middle = TimeStampNode(someNode, time = 2)
    val last = TimeStampNode(someNode, time = 3)

    val sortedTree = TreeSet[TimeStampNode](middle, first, last)
    
    assert(sortedTree.max == last)
    assert(sortedTree.min == first)
  }
}