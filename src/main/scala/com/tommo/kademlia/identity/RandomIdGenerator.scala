package com.tommo.kademlia.identity

import scala.util.Random

class RandomIdGenerator(sizeInBytes: Int, randNumGen: Random = new Random()) extends IdGenerator {
  def generateId(input: Array[Byte]) = {
    val randomArr = new Array[Byte](sizeInBytes)
    randNumGen.nextBytes(randomArr)
    Id(randomArr)
  }
}