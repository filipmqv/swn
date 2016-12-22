package dijkstra

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.language.postfixOps
import scala.concurrent.Await
import scala.concurrent.duration._

class DijkstraActor extends Actor {
  var client: ActorRef = _
  //var nextNode: ActorRef = _
  var myid = -1
  val stateCheckerActor = context.actorOf(Props[StateCheckerActor])
  val criticalSectionActor = context.actorOf(Props[CriticalSectionActor])
  var state = 0
  var isInCriticalSection = false

  def isDistinguished: Boolean = {
    myid == 0
  }

  def receive = {
    case Startup(id, nn) =>
      client = sender
      myid = id
      stateCheckerActor ! Startup(-1, nn)
    case State() =>
      sender ! state
    case StateAnswer(value) =>
      if (isDistinguished) {
        if (state == value && !isInCriticalSection) {
          criticalSectionActor ! Job(value)
          isInCriticalSection = true
          client ! Print(state, 'in)
        }
      } else {
        if (state != value && !isInCriticalSection) {
          criticalSectionActor ! Job(value)
          isInCriticalSection = true
          client ! Print(state, 'in)
        }
      }
    case JobDone(value) =>
      if (isDistinguished) {
        state += 1 // TODO modulo
        isInCriticalSection = false
        client ! Print(state, 'out)
      } else {
        state = value
        isInCriticalSection = false
        client ! Print(state, 'out)
      }
  }
}

class StateCheckerActor extends Actor {
  implicit val timeout = Timeout(100 seconds)
  def receive = {
    case Startup(_, nodeToCheck) =>
      while (true) {
        val future = nodeToCheck ? State()
        val state = Await.result(future, Duration.Inf).asInstanceOf[Int]
        sender ! StateAnswer(state)
        Thread sleep 1000
      }
  }
}


class CriticalSectionActor extends Actor {
  def receive = {
    case Job(value) =>
      // do sth in critical section
      Thread sleep 1500
      sender ! JobDone(value)
  }
}