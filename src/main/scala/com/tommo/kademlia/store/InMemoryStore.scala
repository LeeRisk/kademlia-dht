package com.tommo.kademlia.store

import com.tommo.kademlia.identity.Id
import scala.collection.mutable.Map

class InMemoryStore[V] extends Store[V] {
  private val map = Map[Id, V]()

  def insert(id: Id, v: V) { map += (id -> v) }

  def findCloserThan(source: Id, target: Id) = map.filter {
    case (key, _) =>  
      val keyOrder = new key.SelfOrder
      keyOrder.gt(source, target)
  }.toList

  def get(id: Id) = map.get(id)

  def remove(id: Id) { map -= id }
}

