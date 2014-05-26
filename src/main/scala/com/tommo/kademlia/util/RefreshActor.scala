package com.tommo.kademlia.util

import scala.concurrent.duration._
import scala.collection.mutable.{ PriorityQueue, Map }
import com.tommo.kademlia.misc.time.{ Clock, Epoch }
import RefreshActor._
import akka.actor.{ FSM, ActorRef }

object RefreshActor {
  sealed trait State
  sealed trait Data

  case class RefreshDone(key: Any, value: Any)
  
  case object Running extends State
  case object Empty extends Data

  case class SenderRefresh(sender: ActorRef, refresh: Refresh, insertCount: Long)

  implicit val orderByTime = new Ordering[SenderRefresh] {
    def compare(x: SenderRefresh, y: SenderRefresh): Int =
      getEpochScheduleTime(x.refresh) compare getEpochScheduleTime(y.refresh) match {
        case 0 => x.insertCount compare y.insertCount
        case nonZero => nonZero
      }
  }.reverse

  private def getEpochScheduleTime(refresh: Refresh) = refresh.at + refresh.after.toMillis

  private def getDuration(refresh: Refresh, currTime: Epoch) = (getEpochScheduleTime(refresh) - currTime) match {
    case timeleft if timeleft > 0 => FiniteDuration(timeleft, "millis")
    case _ => 0 millis
  }

  abstract class Refresh {
    val refreshKey: String
    val key: Any
    val value: Any
    val at: Epoch = System.currentTimeMillis()
    val after: Duration
  }
}

class RefreshActor extends FSM[State, Data] {
  self: Clock =>

  val timerName = "refreshTimer"

  var scheduled: Option[SenderRefresh] = None
  var refreshCount = 0L
  val timerMap = Map[String, Map[Any, SenderRefresh]]()
  val pq = PriorityQueue[SenderRefresh]()

  startWith(Running, Empty)

  when(Running) {
    case Event(r: Refresh, _) =>
      val newSR = incrementAndGet(sender, r)
      add(newSR)

      scheduled match {
        case Some(sr) if(sr.refresh.refreshKey == r.refreshKey && sr.refresh.key == r.key) =>
          cancelTimer(timerName)
          startTimer()
        case Some(sr) if (orderByTime.gt(newSR, sr)) =>
          add(sr)
          cancelTimer(timerName)
          startTimer()
        case None => startTimer()
        case _ => 
      }
      
      stay using Empty
    case Event(sr: SenderRefresh, _) =>
      sr.sender ! RefreshDone(sr.refresh.key, sr.refresh.value)
      startTimer()
      stay using Empty
  }

  private def incrementAndGet(sender: ActorRef, r: Refresh) = {
    refreshCount = refreshCount + 1
    SenderRefresh(sender, r, refreshCount)
  }

  private def add(sr: SenderRefresh) {
    import sr.refresh._

    timerMap.get(refreshKey) match {
      case Some(map) =>
        map += key -> sr
      case None =>
        timerMap += refreshKey -> Map(key -> sr)
    }

    pq += sr
  }

  private def dequeue(): Option[SenderRefresh] = {
    pq.size match {
      case 0 => None
      case _ =>
        val deq = pq.dequeue()
        import deq.refresh._
        timerMap.get(refreshKey).get -= key
        Some(deq)
    }
  }

  private def incrementCount() {
    refreshCount = refreshCount + 1
  }

  private def startTimer() {
    dequeue() match {
      case Some(sr) =>
        scheduled = Some(sr)
        setTimer(timerName, sr, getDuration(sr.refresh, getTime()), false)
      case _ => scheduled = None
    }
  }
}
