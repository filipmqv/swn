package dijkstra

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.language.postfixOps
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.forkjoin.ThreadLocalRandom

class DijkstraActor extends Actor {
  var client: ActorRef = _
  var myid = -1
  val stateCheckerActor = context.actorOf(Props[StateCheckerActor])
  val criticalSectionActor = context.actorOf(Props[CriticalSectionActor])
  var state = 0
  var isInCriticalSection = false
  var numberOfNodes = 0

  def isDistinguished: Boolean = {
    myid == 0
  }

  def receive = {
    case Startup(id, nn, noOfNodes) =>
      client = sender
      myid = id
      numberOfNodes = noOfNodes
      stateCheckerActor ! Startup(-1, nn, -1)
    case State() =>
      sender ! state
    case StateAnswer(value) =>
      if (!isInCriticalSection &&
        ((isDistinguished && state == value) || (!isDistinguished && state != value))) {
          criticalSectionActor ! Job(value)
          isInCriticalSection = true
          client ! Print(state, 'in)
        }
    case JobDone(value) =>
      state = if (isDistinguished) (state + 1) % numberOfNodes else value
      isInCriticalSection = false
      client ! Print(state, 'out)
    case DoFail() =>
      state = ThreadLocalRandom.current.nextInt(0, numberOfNodes)
      client ! Print(state, 'out)
  }
}

class StateCheckerActor extends Actor {
  implicit val timeout = Timeout(100 seconds)
  def receive = {
    case Startup(_, nodeToCheck, _) =>
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