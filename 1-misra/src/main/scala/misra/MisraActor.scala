package misra

import akka.actor.{Actor, ActorRef, ActorSelection}

class MisraActor extends Actor {
  var id: Int = -1
  var nextNode: ActorSelection = _
  var client: ActorRef = _

  def receive = {
    case Startup(i, address) =>
      id = i
      nextNode = address
      client = sender()
      println(i + " " + self.path.name + " got nextNode")
      if (i == 0)
        nextNode ! Ping(0)
    case Ping(value) =>
      println(id + " " + self.path.name + " got ping from " + sender.path + " " + value)
      Thread sleep 1000
      nextNode ! Ping(value + 1)
  }
}