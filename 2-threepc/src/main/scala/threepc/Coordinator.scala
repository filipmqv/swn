package threepc

import akka.actor.{Actor, ActorRef}

class Coordinator extends Actor {
  import context._ // for become method
  var cohorts = Map.empty[ActorRef, Int]
  var client: ActorRef = _
  var cohortsAgreedNumber = 0
  var cohortsAckedNumber = 0

  def sleep(milliseconds: Int): Unit = {
    Thread sleep milliseconds * 2
  }

  def receive = {
    case StartupCoordinator(c) =>
      client = sender
      cohorts = c
      println("coordinator start")
      self ! StartCommit(1)
      become(pending)
  }

  def pending: Receive = {
    case StartCommit(commitId) =>
      cohortsAgreedNumber = 0
      cohortsAckedNumber = 0
      client ! Print(cohorts.keys.toList, 'send, 'CommitRequest, commitId, 'waiting)
      sleep(1000)
      cohorts foreach { case (cohort, id) => cohort ! CommitRequest(commitId) }
      become(waiting)
  }

  def waiting: Receive = {
    case Agree(commitId) =>
      cohortsAgreedNumber += 1
      client ! Print(List(sender), 'got, 'Agree, commitId, 'waiting)
      if (cohortsAgreedNumber == cohorts.size) { // all agreed
        sleep(1000)
        client ! Print(cohorts.keys.toList, 'send, 'Prepare, commitId, 'prepared)
        sleep(1000)
        cohorts foreach { case (cohort, id) => cohort ! Prepare(commitId) }
        become(prepared)
      }
    case Abort(commitId) =>
      client ! Print(List(sender), 'got, 'Abort, commitId, 'waiting)
      sleep(1000)
      client ! Print(cohorts.keys.toList, 'send, 'Abort, commitId, 'aborted)
      cohorts foreach { case (cohort, id) => cohort ! Abort(commitId) }
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
        client ! Print(cohorts.keys.toList, 'send, 'Commit, commitId, 'commited)
        sleep(1000)
        cohorts foreach { case (cohort, id) => cohort ! Commit(commitId) }
        sleep(1000)
        self ! CommitFinished(commitId)
        become(commited)
      }
  }

  def commited: Receive = {
    case CommitFinished(commitId) =>
      println("finished")
      become(pending)
  }

  def aborted: Receive = {
    case CommitFinished(commitId) =>
      println("aborted")
      become(pending)
  }
}
