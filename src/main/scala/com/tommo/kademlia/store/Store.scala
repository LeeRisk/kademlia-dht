package com.tommo.kademlia.store

import com.tommo.kademlia.identity.Id

trait Store[V] {
	def insert(id: Id, v: V)
	def get(id: Id): Option[Set[V]]
	def remove(id: Id, v: V)
	def getCloserThan(source: Id, target: Id): Map[Id, Set[V]] 
}
