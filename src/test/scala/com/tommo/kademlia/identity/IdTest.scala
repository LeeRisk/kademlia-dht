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

  it should "fail if address space size do not match for distance calculation" in {
    val id = Id("10011")
    val anotherId = Id("111111")

    intercept[RuntimeException] {
      id distance anotherId
    }
  }

  it should "fail if constructed bit string contains invalid characters" in {
    val bleh = new RandomIdGenerator(2)

    intercept[RuntimeException] {
      Id("01InvalidSeq10")
    }
  }
}