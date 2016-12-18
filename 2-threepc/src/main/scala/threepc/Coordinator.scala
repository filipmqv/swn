package threepc

import akka.actor.{Actor, ActorRef}
import scala.concurrent.duration._
import scala.language.postfixOps

class Coordinator extends Actor {
  import context._ // for become method
  var cohorts = Map.empty[ActorRef, Int]
  var client: ActorRef = _
  var cohortsAgreedNumber = 0
  var cohortsAckedNumber = 0

  def sleep(milliseconds: Int): Unit = {
    Thread sleep milliseconds * 2
  }

  def broadcast(msg: Msg) = {
    // simulate channel delay
    val requestors = cohorts
    val msgToSend = msg
    context.system.scheduler.scheduleOnce(1000 milliseconds) {
      requestors foreach { _._1 ! msgToSend }
    }
  }

  def broadcastAndPrint(msg: Msg, state: Symbol) = {
    val (meessageType, commitId) = msg match {
      case CommitRequest(i) => ('CommitRequest, i)
      case Prepare(i) => ('Prepare, i)
      case Commit(i) => ('Commit, i)
      case Abort(i) => ('Abort, i)
      case _ => ('Other, -1)
    }
    client ! Print(cohorts.keys.toList, 'send, meessageType, commitId, state)
    broadcast(msg)
  }

  def receive = {
    case StartupCoordinator(c) =>
      client = sender
      cohorts = c
      self ! StartCommit(1)
      become(pending)
  }

  def pending: Receive = {
    case StartCommit(commitId) =>
      cohortsAgreedNumber = 0
      cohortsAckedNumber = 0
      broadcastAndPrint(CommitRequest(commitId), 'waiting)
      become(waiting)
  }

  def waiting: Receive = {
    case Agree(commitId) =>
      cohortsAgreedNumber += 1
      client ! Print(List(sender), 'got, 'Agree, commitId, 'waiting)
      if (cohortsAgreedNumber == cohorts.size) { // all agreed
        sleep(1000)
        broadcastAndPrint(Prepare(commitId), 'prepared)
        become(prepared)
      }
    case Abort(commitId) =>
      client ! Print(List(sender), 'got, 'Abort, commitId, 'waiting)
      sleep(1000)
      broadcastAndPrint(Abort(commitId), 'aborted)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(aborted)
  }

  def prepared: Receive = {
    case Ack(commitId) =>
      cohortsAckedNumber += 1
      client ! Print(List(sender), 'got, 'Ack, commitId, 'prepared)
      if (cohortsAckedNumber == cohorts.size) { // all sent ack
        sleep(1000)
        broadcastAndPrint(Commit(commitId), 'commited)
        sleep(1000)
        self ! CommitFinished(commitId)
        become(commited)
      }
  }

  def commited: Receive = {
    case CommitFinished(commitId) =>
      println("finished")
      sleep(2000)
      self ! StartCommit(commitId + 1)
      become(pending)
  }

  def aborted: Receive = {
    case CommitFinished(commitId) =>
      println("aborted")
      sleep(2000)
      self ! StartCommit(commitId + 1)
      become(pending)
  }
}
