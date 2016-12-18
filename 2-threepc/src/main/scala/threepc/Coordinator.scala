package threepc

import akka.actor.{Actor, ActorRef, Cancellable}

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
    // TODO random delay
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

  def setTimeout(commitId: Int): Cancellable = {
    val requestor = self
    val msgToSend = TimeIsOut(commitId)
    system.scheduler.scheduleOnce(10 seconds) {
      requestor ! msgToSend
    }
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
      val calcellable = setTimeout(commitId)
      become(waiting(calcellable))
  }

  def waiting(cancelTimeout: Cancellable): Receive = {
    case Agree(commitId) =>
      cohortsAgreedNumber += 1
      client ! Print(List(sender), 'got, 'Agree, commitId, 'waiting)
      if (cohortsAgreedNumber == cohorts.size) { // all agreed
        cancelTimeout.cancel()
        sleep(1000)
        broadcastAndPrint(Prepare(commitId), 'prepared)
        val calcellable = setTimeout(commitId)
        become(prepared(calcellable))
      }
    case Abort(commitId) =>
      cancelTimeout.cancel()
      client ! Print(List(sender), 'got, 'Abort, commitId, 'waiting)
      sleep(1000)
      broadcastAndPrint(Abort(commitId), 'aborted)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(aborted)
    case TimeIsOut(commitId) =>
      broadcastAndPrint(Abort(commitId), 'aborted)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(aborted)
  }

  def prepared(cancelTimeout: Cancellable): Receive = {
    case Ack(commitId) =>
      cohortsAckedNumber += 1
      client ! Print(List(sender), 'got, 'Ack, commitId, 'prepared)
      if (cohortsAckedNumber == cohorts.size) { // all sent ack
        cancelTimeout.cancel()
        sleep(1000)
        broadcastAndPrint(Commit(commitId), 'commited)
        sleep(1000)
        self ! CommitFinished(commitId)
        become(commited)
      }
    case TimeIsOut(commitId) =>
      broadcastAndPrint(Abort(commitId), 'aborted)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(aborted)
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
