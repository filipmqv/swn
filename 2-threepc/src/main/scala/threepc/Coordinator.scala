package threepc

import akka.actor.{Actor, ActorRef}

class Coordinator extends Actor {
  import context._ // for become method
  var cohorts = Map.empty[ActorRef, Int]
  var cohortsAgreedNumber = 0
  var cohortsAckedNumber = 0

  def sleep(milliseconds: Int): Unit = {
    Thread sleep milliseconds
  }

  def receive = {
    case StartupCoordinator(c) =>
      cohorts = c
      println("coordinator start")
      self ! StartCommit(1)
      become(pending)
  }

  def pending: Receive = {
    case StartCommit(commitId) =>
      cohortsAgreedNumber = 0
      cohortsAckedNumber = 0
      println("sending CommitRequests ")
      cohorts foreach { case (cohort, id) => cohort ! CommitRequest(commitId) }
      become(waiting)
  }

  def waiting: Receive = {
    case Agree(commitId) =>
      cohortsAgreedNumber += 1
      if (cohortsAgreedNumber == cohorts.size) { // all agreed
        cohorts foreach { case (cohort, id) => cohort ! Prepare(commitId) }
        println("all agreed")
        become(prepared)
      }
    case Abort(commitId) =>
      ??? // TODO
  }

  def prepared: Receive = {
    case Ack(commitId) =>
      cohortsAckedNumber += 1
      if (cohortsAckedNumber == cohorts.size) { // all sent ack
        cohorts foreach { case (cohort, id) => cohort ! Commit(commitId) }
        println("all acked1")
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
}
