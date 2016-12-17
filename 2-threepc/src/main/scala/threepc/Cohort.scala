package threepc

import akka.actor.Actor

class Cohort extends Actor {
  import context._ // for become method
  var myid = -1

  def receive = {
    case StartupCohort(id) =>
      myid = id
      println(id + "becoming pending")
      become(pending)
  }

  def pending: Receive = {
    case Text(sdsad) => val a = 3
  }
}
