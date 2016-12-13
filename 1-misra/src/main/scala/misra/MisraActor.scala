package misra

import akka.actor.{Actor, ActorRef, Props}

import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.duration._
import scala.language.postfixOps

class MisraActor extends Actor {
  import context._

  var id: Int = -1
  var nextNode: ActorRef = _
  var client: ActorRef = _
  var tokenLastValue = 0
  val criticalSectionActor = context.actorOf(Props[CriticalSectionActor])
  val pongDelayerActor = context.actorOf(Props[PongDelayerActor])
  // lose token during "before" receiving
  var losePing = false
  var losePong = false

  def sendPing(value: Int) = {
    tokenLastValue = value
    client ! Print(nextNode, 'sendping, value)

    // simulate channel delay
    val requestor = nextNode
    val valueToSend = value
    context.system.scheduler.scheduleOnce(500 milliseconds) {
      requestor ! Ping(valueToSend)
    }
  }

  def sendPong(value: Int) = {
    tokenLastValue = value
    client ! Print(nextNode, 'sendpong, value)

    // simulate channel delay
    val requestor = nextNode
    val valueToSend = value
    context.system.scheduler.scheduleOnce(500 milliseconds) {
      requestor ! Pong(valueToSend)
    }
  }

  def loseMessage(which: Symbol) = {
    which match {
      case 'ping => losePing = true
      case 'pong => losePong = true
    }
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
      if (losePing) {
        losePing = false
      } else {
        client ! Print(sender, 'gotping, value)
        // if values are the same it means that pong is lost
        if (value == tokenLastValue) {
          client ! Print(sender, 'gotpong, -value-1) // recreating pong actually
          criticalSectionActor ! Job(value)
          become(gotPongDuringCriticalSection)
        } else {
          criticalSectionActor ! Job(value)
          become(inCriticalSection)
        }
      }
    case Pong(value) =>
      if (losePong) {
        losePong = false
      } else {
        client ! Print(sender, 'gotpong, value)
        if (value == tokenLastValue) {
          client ! Print(sender, 'gotping, -value+1) // recreating ping actually
          criticalSectionActor ! Job(-value)
          become(gotPongDuringCriticalSection)
        } else {
          pongDelayerActor ! Wait(value)
          become(gotPong)
        }
      }
    case LoseMessage(which) =>
      loseMessage(which)
  }

  def inCriticalSection: Receive = {
    case JobDone(value) =>
      sendPing(value)
      become(pending)
    case Pong(value) =>
      if (losePong) {
        losePong = false
      } else {
        client ! Print(sender, 'gotpong, value)
        become(gotPongDuringCriticalSection)
      }
    case Ping(value) =>
      // TODO error - second ping came
      client ! Print(sender, 'errorping, value)
    case LoseMessage(which) =>
      loseMessage(which)
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
    case LoseMessage(which) =>
      loseMessage(which)
  }

  def gotPong: Receive = {
    case WaitDone(value) =>
      sendPong(value)
      become(pending)
    case Ping(value) =>
      if (losePing) {
        losePing = false
      } else {
        client ! Print(sender, 'gotping, value)
        criticalSectionActor ! Job(value)
        become(gotPongDuringCriticalSection)
      }
    case Pong(value) =>
      // TODO error - second Pong came
      client ! Print(sender, 'errorpong, value)
    case LoseMessage(which) =>
      loseMessage(which)
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