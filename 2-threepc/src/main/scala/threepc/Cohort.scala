package threepc

import akka.actor.{Actor, ActorRef}

class Cohort extends Actor {
  import context._ // for become method
  var client: ActorRef = _
  var myid = -1
  var currentCommitId: Int = 0

  def sleep(milliseconds: Int): Unit = {
    Thread sleep milliseconds * 2
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
      client ! Print(List(sender), 'send, 'Agree, commitId, 'waiting)
      sleep(1000)
      sender ! Agree(commitId)
      become(waiting)
  }

  def waiting: Receive = {
    case Prepare(commitId) =>
      client ! Print(List(sender), 'got, 'Prepare, commitId, 'waiting)
      sleep(1000)
      client ! Print(List(sender), 'send, 'Ack, commitId, 'prepared)
      sleep(1000)
      sender ! Ack(commitId)
      become(prepared)
  }

  def prepared: Receive = {
    case Commit(commitId) =>
      client ! Print(List(sender), 'got, 'Commit, commitId, 'commited)
      sleep(1000)
      self ! CommitFinished(commitId)
      become(commited)
  }

  def commited: Receive = {
    case CommitFinished(commitId) =>
      become(pending)
  }
}
