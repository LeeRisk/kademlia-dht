package com.tommo.kademlia.identity

import com.tommo.kademlia.BaseUnitTest
import org.scalatest._

class IdTest extends BaseUnitTest {
  def idFactory = Id(8)_

  "An Id" should "be able to compute distance between a node with the same address size" in {
    val id = idFactory(8)
    val anotherId = idFactory(3)

    val expectedDistance = idFactory(11)

    assert(id.distance(anotherId) == expectedDistance)
  }

  it should "construct a string of bits using little endian" in {
    val id = Id("10")
    id.bits should contain inOrderOnly (true, false)
  }

  it should "return the longest common prefix size" in {
    val id = Id("10010011")
    val anotherId = Id("10011011")

    assert(id.longestPrefixLength(anotherId) == 4)
  }

  it should "fail if address space size do not match for distance calculation" in {
    val id = Id("10011")
    val anotherId = Id("111111")

    intercept[RuntimeException] {
      id distance anotherId
    }
  }

  it should "fail if constructed bit string contains invalid characters" in {
    intercept[RuntimeException] {
      Id("01InvalidSeq10")
    }
  }

  it should "find all common prefixes starting from left" in {
    val id        = Id("0000")
    val anotherId = Id("0101")
    
    id.scanLeftPrefix(anotherId) should contain inOrderOnly (2,0)
    
    
    id.scanLeftPrefix(id) shouldBe empty
  }

}
