package com.tommo

package object kademlia {
  val EnableInvariantCheck = true
  
  def invariant(predicate: => Boolean, errorMsg: String) {
    if(EnableInvariantCheck && predicate)  
      throw new IllegalArgumentException(errorMsg)
  }
}