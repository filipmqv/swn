package threepc

import akka.actor.{Actor, ActorRef, Cancellable}

import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.duration._
import scala.language.postfixOps

class Cohort extends Actor {
  import context._ // for become method
  var client: ActorRef = _
  var myid = -1
  var currentCommitId: Int = 0

  def sleep(milliseconds: Int): Unit = {
    Thread sleep milliseconds
  }

  def performFail(state: Symbol = 'aborted) = {
    client ! Print(List(sender), 'got, 'DoFail, currentCommitId, state)
    sleep(10000)
    self ! CommitFinished(currentCommitId)
    state match {
      case 'aborted => become(aborted)
      case _ => become(commited)
    }
  }

  def send(requestor: ActorRef, msg: Msg) = {
    // simulate random channel delay
    context.system.scheduler.scheduleOnce(ThreadLocalRandom.current.nextInt(500, 2000) milliseconds) {
      requestor ! msg
    }
  }

  def sendAndPrint(requestor: ActorRef, msg: Msg, state: Symbol) = {
    val (meessageType, commitId) = msg match {
      case Agree(i) => ('Agree, i)
      case Ack(i) => ('Ack, i)
      case CommitFinished(i) => ('CommitFinished, i)
      case Abort(i) => ('Abort, i)
      case _ => ('Other, -1)
    }
    client ! Print(List(requestor), 'send, meessageType, commitId, state)
    send(requestor, msg)
  }

  def setTimeout(commitId: Int): Cancellable = {
    val requestor = self
    val msgToSend = TimeIsOut(commitId)
    system.scheduler.scheduleOnce(10 seconds) {
      requestor ! msgToSend
    }
  }


  def receive = {
    case StartupCohort(id) =>
      client = sender
      myid = id
      println(id + " becoming pending")
      become(pending)
  }

  def pending: Receive = {
    case CommitRequest(commitId) =>
      client ! Print(List(sender), 'got, 'CommitRequest, commitId, 'waiting)
      currentCommitId = commitId
      sleep(1000)
      // TODO choice by user input, not random
      ThreadLocalRandom.current.nextDouble(0, 1) match {
        case i if i < 0.8 =>
          sendAndPrint(sender, Agree(commitId), 'waiting)
          val calcellable = setTimeout(commitId)
          become(waiting(calcellable))
        case _ =>
          sendAndPrint(sender, Abort(commitId), 'aborted)
          sleep(1000)
          self ! CommitFinished(commitId)
          become(aborted)
      }
    case DoFail() =>
      performFail()
  }

  def waiting(cancelTimeout: Cancellable): Receive = {
    case Prepare(commitId) =>
      cancelTimeout.cancel()
      client ! Print(List(sender), 'got, 'Prepare, commitId, 'waiting)
      sleep(1000)
      sendAndPrint(sender, Ack(commitId), 'prepared)
      val calcellable = setTimeout(commitId)
      become(prepared(calcellable))
    case Abort(commitId) =>
      cancelTimeout.cancel()
      client ! Print(List(sender), 'got, 'Abort, commitId, 'aborted)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(aborted)
    case TimeIsOut(commitId) =>
      client ! Print(List(sender), 'got, 'Other, commitId, 'aborted)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(aborted)
    case DoFail() =>
      cancelTimeout.cancel()
      performFail()
  }

  def prepared(cancelTimeout: Cancellable): Receive = {
    case Commit(commitId) =>
      cancelTimeout.cancel()
      client ! Print(List(sender), 'got, 'Commit, commitId, 'commited)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(commited)
    case Abort(commitId) =>
      cancelTimeout.cancel()
      client ! Print(List(sender), 'got, 'Abort, commitId, 'aborted)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(aborted)
    case TimeIsOut(commitId) =>
      client ! Print(List(sender), 'got, 'Other, commitId, 'commited)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(commited)
    case DoFail() =>
      cancelTimeout.cancel()
      performFail('commited)
  }

  def commited: Receive = {
    case CommitFinished(commitId) =>
      client ! Print(List(sender), 'got, 'Other, commitId, 'pending)
      become(pending)
  }

  def aborted: Receive = {
    case CommitFinished(commitId) =>
      client ! Print(List(sender), 'got, 'Other, commitId, 'pending)
      become(pending)
  }
}
