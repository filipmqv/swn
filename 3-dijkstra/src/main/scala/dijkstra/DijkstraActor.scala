package dijkstra

import akka.actor.{Actor, ActorRef}

class DijkstraActor extends Actor {
  var client: ActorRef = _
  var nextNode: ActorRef = _
  var myid = -1


  def receive = {
    case Startup(id, nn) =>
      client = sender
      nextNode = nn
      myid = id
      if (id == 0) { // different node
        // TODO
      }
  }
}
