package com.tommo.kademlia.identity

import java.security.MessageDigest

class HashIdGenerator(md: MessageDigest) extends ByteStreamIdGenerator {
  
  def generateId(input: Array[Byte]) = {
    md.reset()
    val c = md.digest(input);
    
    Id(md.digest(input)) 
  }
}

object HashIdGenerator {
  def apply(hashAlgorithm: String) = new HashIdGenerator(MessageDigest.getInstance(hashAlgorithm))
}