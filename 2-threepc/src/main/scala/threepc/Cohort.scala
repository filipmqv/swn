package threepc

import akka.actor.{Actor, ActorRef}

import scala.concurrent.forkjoin.ThreadLocalRandom

class Cohort extends Actor {
  import context._ // for become method
  var client: ActorRef = _
  var myid = -1
  var currentCommitId: Int = 0

  def sleep(milliseconds: Int): Unit = {
    Thread sleep milliseconds * 2
  }

  def performFail() = {
    client ! Print(List(sender), 'got, 'DoFail, -1, 'aborted)
    self ! CommitFinished(currentCommitId)
    become(aborted)
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
          client ! Print(List(sender), 'send, 'Agree, commitId, 'waiting)
          sleep(1000)
          sender ! Agree(commitId)
          become(waiting)
        case _ =>
          client ! Print(List(sender), 'send, 'Abort, commitId, 'aborted)
          sleep(1000)
          sender ! Abort(commitId)
          sleep(1000)
          self ! CommitFinished(commitId)
          become(aborted)
      }
    case DoFail() =>
      performFail()
  }

  def waiting: Receive = {
    case Prepare(commitId) =>
      client ! Print(List(sender), 'got, 'Prepare, commitId, 'waiting)
      sleep(1000)
      client ! Print(List(sender), 'send, 'Ack, commitId, 'prepared)
      sleep(1000)
      sender ! Ack(commitId)
      become(prepared)
    case Abort(commitId) =>
      client ! Print(List(sender), 'got, 'Abort, commitId, 'aborted)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(aborted)
    case DoFail() =>
      performFail()
  }

  def prepared: Receive = {
    case Commit(commitId) =>
      client ! Print(List(sender), 'got, 'Commit, commitId, 'commited)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(commited)
    case Abort(commitId) =>
      client ! Print(List(sender), 'got, 'Abort, commitId, 'aborted)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(aborted)
    case DoFail() =>
      client ! Print(List(sender), 'got, 'DoFail, -1, 'commited)
      self ! CommitFinished(currentCommitId)
      become(commited)
  }

  def commited: Receive = {
    case CommitFinished(commitId) =>
      become(pending)
  }

  def aborted: Receive = {
    case CommitFinished(commitId) =>
      become(pending)
  }
}
