package com.tommo.kademlia.store

import com.tommo.kademlia.identity.Id

class InMemoryStore[V] extends Store[V] {
  private var map = Map[Id, Set[V]]()

  def insert(id: Id, v: V) {
    map.get(id) match {
      case Some(s) => map += (id -> (s + v))
      case None => map += (id -> Set(v))
    }
  }

  def getCloserThan(source: Id, target: Id) = map.filter{
    case (key, _) =>  
      val keyOrder = new key.SelfOrder
      keyOrder.gt(source, target)
  }

  def get(id: Id) = Some(map.get(id).get)

  def remove(id: Id, v: V) {
    map.get(id) match {
      case Some(s) if s.contains(v) => map += (id -> (s - v)) 
      case _ =>
    }
  }
}

