package threepc

import akka.actor.Actor

class Cohort extends Actor {
  import context._ // for become method
  var myid = -1
  var currentCommitId: Int = 0

  def sleep(milliseconds: Int): Unit = {
    Thread sleep milliseconds
  }

  def receive = {
    case StartupCohort(id) =>
      myid = id
      println(id + " becoming pending")
      become(pending)
  }

  def pending: Receive = {
    case CommitRequest(commitId) =>
      currentCommitId = commitId
      sleep(1000)
      sender ! Agree(commitId)
      become(waiting)
  }

  def waiting: Receive = {
    case Prepare(commitId) =>
      sleep(1000)
      sender ! Ack(commitId)
      become(prepared)
  }

  def prepared: Receive = {
    case Commit(commitId) =>
      sleep(1000)
      self ! CommitFinished(commitId)
      become(commited)
  }

  def commited: Receive = {
    case CommitFinished(commitId) =>
      become(pending)
  }
}
