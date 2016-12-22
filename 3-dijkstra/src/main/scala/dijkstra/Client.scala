package dijkstra

import akka.actor.{Actor, ActorRef, ActorSystem, Address, Cancellable, Props, RelativeActorPath, RootActorPath}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

object Client {
  def main(args: Array[String]): Unit = {
    // note that client is not a compute node, role not defined
    val system = ActorSystem("ClusterSystem")
    system.actorOf(Props(classOf[ClientActor], "/user/dijkstraService", args(0).toInt), "client")
  }
}

class ClientActor(servicePath: String, clusterSize: Int) extends Actor {
  val cluster = Cluster(context.system)
  val servicePathElements = servicePath match {
    case RelativeActorPath(elements) => elements
    case _ => throw new IllegalArgumentException(
      "servicePath [%s] is not a valid relative actor path" format servicePath)
  }
  val failureActor = cluster.system.actorOf(Props[FailureActor])

  var nodeIndex = -1
  var nodes = Set.empty[Address]
  var dijkstraActors = Map.empty[ActorRef, Int]
  var states = Map.empty[ActorRef, (Symbol, Int)] // node, (in or out of critical section, state number)
  val defaultConsoleInfoBarText = "Press [ENTER] to cause state shuffle"
  var consoleInfoBar = defaultConsoleInfoBarText
  var consoleInfoBarSchedulerCancel: Cancellable = _

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive = {
    case Print(state, action) =>
      states += (sender -> ((action, state)))
      print("\n\n\n\n\n\n\n\n\n")
      dijkstraActors foreach {
        case (actor, id) =>
          print("id st    ")
      }
      println()
      dijkstraActors foreach {
        case (actor, id) =>
          print(f"$id%2d ${states(actor)._2}%2d    ")
      }
      println()
      dijkstraActors foreach {
        case (actor, id) =>
          states(actor)._1 match {
            case 'in =>
              print("******")
            case _ =>
              print("      ")
          }
          print("   ")
      }
      println()
      println(consoleInfoBar)

    case MemberUp(m) if m.hasRole("actor") =>
      nodes += m.address
      implicit val resolveTimeout = Timeout(5 seconds)
      nodeIndex += 1 // first node is node_0
      dijkstraActors += (Await.result(context.actorSelection(RootActorPath(m.address) / servicePathElements)
        .resolveOne(), resolveTimeout.duration) -> nodeIndex)
      if (nodes.size == clusterSize) {
        // tell actors about next nodes
        for (i <- 0 until dijkstraActors.size) {
          val currentNode = dijkstraActors.toIndexedSeq(i)._1
          val nextNode = dijkstraActors.toIndexedSeq((i+1)%dijkstraActors.size)._1
          currentNode ! Startup(dijkstraActors(currentNode), nextNode, dijkstraActors.size)
          states += (currentNode -> (('out, 0))) // for printing
        }
        // send actorRefs to actor which handles failures
        failureActor ! Nodes(dijkstraActors)
      }

    case other: MemberEvent                         => nodes -= other.member.address
    case UnreachableMember(m)                       => nodes -= m.address
    case ReachableMember(m) if m.hasRole("compute") => nodes += m.address
  }

}

class FailureActor extends Actor {
  def receive = {
    case Nodes(actors) =>
      while (true) {
          scala.io.StdIn.readLine()
          actors foreach { _._1 ! DoFail() }
      }
  }
}