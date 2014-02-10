package com.tommo.kademlia.identity

import scala.util.Random

trait IdGenerator  {
  import IdGenerator._
  
  def generateId(addressSpace: Int) = {
    val randomArr = new Array[Byte](Math.ceil(addressSpace / 8).toInt)
    randNumGen.nextBytes(randomArr)
    Id(randomArr.take(addressSpace))
  }
}

object IdGenerator {
  private val randNumGen: Random = new Random()
}