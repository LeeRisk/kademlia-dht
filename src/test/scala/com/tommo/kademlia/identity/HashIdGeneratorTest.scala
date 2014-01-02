package com.tommo.kademlia.identity

import com.tommo.kademlia.BaseUnitTest
import org.scalatest._

class HashIdGeneratorTest extends BaseUnitTest {
  "A HashIdGenerator" should "correctly generate the hash that is defined by the hashing algorithm" in {
    val hasher = HashIdGenerator("SHA-1")
    
    val (dataInput, expectedId) = ("this is a test".getBytes(), Id(new sun.misc.BASE64Decoder().decodeBuffer("+ia+Gd5r/5P3C8IwhDTkpEC7rQI=")))
    
    assert(hasher.generateId(dataInput) == expectedId)
  }
}