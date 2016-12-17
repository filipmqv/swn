package threepc

import akka.actor.{Actor, ActorRef}

class Coordinator extends Actor {
  import context._ // for become method
  var cohorts = Map.empty[ActorRef, Int]


  def receive = {
    case StartupCoordinator(c) =>
      cohorts = c
      println("coordinator start")
      become(pending)
  }

  def pending: Receive = {
    case Text(sdsad) => val a = 3
  }
}
