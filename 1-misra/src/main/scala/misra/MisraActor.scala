package misra

import akka.actor.{Actor, ActorRef, Props}

import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.language.postfixOps

class MisraActor extends Actor {
  import context._
  var id: Int = -1
  var nextNode: ActorRef = _
  var client: ActorRef = _
  var tokenLastValue = 0
  val criticalSectionActor = context.actorOf(Props[CriticalSectionActor])
  val pongDelayerActor = context.actorOf(Props[PongDelayerActor])

  def sendPing(value: Int) = {
    tokenLastValue = value
    client ! Print(nextNode, 'sendping, value)
    Thread sleep 500
    nextNode ! Ping(value)
  }

  def sendPong(value: Int) = {
    tokenLastValue = value
    client ! Print(nextNode, 'sendpong, value)
    Thread sleep 500
    nextNode ! Pong(value)
  }

  def receive = {
    case Startup(i, address) =>
      id = i
      nextNode = address
      client = sender
      println(i + " " + self.path.name + " STARTUP")
      become(pending)
  }

  def pending: Receive = {
    case Ping(value) =>
      client ! Print(sender, 'gotping, value)
      val newValue = if (value == tokenLastValue) {
        sendPong(-value - 1)
        value + 1
      } else {
        value
      }
      criticalSectionActor ! Job(newValue)
      become(inCriticalSection)
    case Pong(value) =>
      client ! Print(sender, 'gotpong, value)
      val newValue = if (value == tokenLastValue) {
        sendPing(-value + 1)
        value - 1
      } else {
        value
      }
      pongDelayerActor ! Wait(newValue)
      become(gotPong)
  }

  def inCriticalSection: Receive = {
    case JobDone(value) =>
      sendPing(value)
      become(pending)
    case Pong(value) =>
      client ! Print(sender, 'gotpong, value)
      become(gotPongDuringCriticalSection)
    case Ping(value) =>
      // TODO error - second ping came
      client ! Print(sender, 'errorping, value)
  }

  def gotPongDuringCriticalSection: Receive = {
    case JobDone(value) =>
      sendPing(value + 1)
      pongDelayerActor ! Wait(-value - 1)
      become(gotPong)
    case Ping(value) =>
      // TODO error - second Ping came
      client ! Print(sender, 'errorping, value)
    case Pong(value) =>
      // TODO error - second Pong came
      client ! Print(sender, 'errorpong, value)
  }

  def gotPong: Receive = {
    case WaitDone(value) =>
      sendPong(value)
      become(pending)
    case Ping(value) =>
      client ! Print(sender, 'gotping, value)
      criticalSectionActor ! Job(value)
      become(gotPongDuringCriticalSection)
    case Pong(value) =>
      // TODO error - second Pong came
      client ! Print(sender, 'errorpong, value)
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

class PongDelayerActor extends Actor {
  def receive = {
    case Wait(value) =>
      Thread sleep (1500 + ThreadLocalRandom.current.nextInt(1000))
      sender ! WaitDone(value)
  }
}