package com.tommo.kademlia.identity

import java.security.MessageDigest

class HashIdGenerator(md: MessageDigest) extends IdGenerator {
  
  def generateId(input: Array[Byte]) = {
    md.reset()
    Id(md.digest(input)) 
  }
}

object HashIdGenerator {
  def apply(hashAlgorithm: String) = new HashIdGenerator(MessageDigest.getInstance(hashAlgorithm))
}