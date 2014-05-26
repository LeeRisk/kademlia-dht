package com.tommo.kademlia.identity
import scala.annotation.tailrec

import Id._

class Id private (val decimalVal: BigInt, val size: Int) {
  def distance(to: Id): Id = {
    require(size == to.size, "Must have same address space size to compute distance")
    new Id(decimalVal ^ to.decimalVal, size)
  }

  def longestPrefixLength(other: Id) = {
    val dist = distance(other)

    @tailrec
    def _longestPrefixLength(currentBit: Int): Int = currentBit match {
      case 0 => dist.size
      case _ if dist.isBitSet(currentBit) => dist.size - currentBit
      case _ => _longestPrefixLength(currentBit - 1)
    }

    _longestPrefixLength(dist.size)
  }

  def findAllNonMatchingFromRight(other: Id) = foldLeft(List[Int]()) {
    case (bit, list) => if (other.isBitSet(bit) != isBitSet(bit)) (bit - 1) :: list else list
  }.reverse

  override def toString = str

  lazy val str = {
    val binary = decimalVal.toString(2)
    val zeroPad = (for (i <- 1 to size - binary.length()) yield ('0'))(collection.breakOut)
    zeroPad + binary
  }

  class SelfOrder extends Ordering[Id] { // closeness relative to "this" id

    override def compare(x: Id, y: Id) = {
      val xDistInt = distance(x).decimalVal
      val yDistInt = distance(y).decimalVal

      xDistInt.compare(yDistInt)
    }
  }

  lazy val flip = foldLeft(new Id(BigInt(0), size)) {
    case (bit, id) => if (!isBitSet(bit)) id.setBit(bit) else id
  }

  private def takeRight(n: Int) = foldLeft(new Id(BigInt(0), n), Math.min(n, decimalVal.bitLength)) {
    case (bit, id) => if (isBitSet(bit)) id.setBit(bit) else id
  }

  private def foldLeft[T](i: T, start: Int = size)(op: (Int, T) => T) = {
    def _foldLeft(bit: Int = start, acc: T = i): T = bit match {
      case 0 => acc
      case _ => _foldLeft(bit - 1, op(bit, acc))
    }
    _foldLeft()
  }

  private def isBitSet(bit: Int) = decimalVal.testBit(bit - 1)
  private def setBit(bit: Int) = new Id(decimalVal.setBit(bit - 1), size)

  override def equals(o: Any) = o match {
    case that: Id => that.decimalVal.equals(decimalVal) && that.size.equals(size)
    case _ => false
  }
  
   override def hashCode() =  (41 * (41 + decimalVal) + size).hashCode;

}

object Id {
  def unapply(id: Id) = Some(id.toString)

  private def toUnsigned(bytes: Array[Byte]): BigInt = {
    def _toUnsigned(index: Int = 0, decVal: BigInt = BigInt(0)): BigInt = if (index == bytes.length) decVal else _toUnsigned(index + 1, (decVal << 8) + (bytes(index) & 0xff))
    _toUnsigned()
  }

  def apply(bytes: Array[Byte]) = { // a byte array whose value can range from [0, bytes * 8)
    val decVal = toUnsigned(bytes)
    new Id(decVal, bytes.length * 8)
  }

  def apply(bitStr: String) = {
    require(bitStr.matches("[01]+"), "String can only contain 0s or 1s")

    val (decVal, _) = bitStr.foldRight((BigInt(0), 0)) {
      case (c, (sum, index)) if (c == '1') => { (sum.setBit(index), index + 1) }
      case (c, (sum, index)) if (c == '0') => (sum, index + 1)
    }

    new Id(decVal, bitStr.length())
  }

  def apply(idSize: Int)(decimalVal: BigInt): Id = {
    require(decimalVal >= 0, "Value can not be less than 0")
    require(idSize >= decimalVal.bitLength, s"idSize must be greater than the unsigned binary representation of $decimalVal")
    new Id(decimalVal, idSize)
  }
}
