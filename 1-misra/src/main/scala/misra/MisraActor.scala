package misra

import akka.actor.{Actor, ActorRef}

class MisraActor extends Actor {
  var id: Int = -1
  var nextNode: ActorRef = _
  var client: ActorRef = _

  def receive = {
    case Startup(i, address) =>
      id = i
      nextNode = address
      client = sender()
      println(i + " " + self.path.name + " got nextNode")
    case Ping(value) =>
      client ! Print(s" got $value from $sender")
      Thread sleep 1000
      nextNode ! Ping(value + 1)
      client ! Print(s" sent $value to $nextNode")
  }
}