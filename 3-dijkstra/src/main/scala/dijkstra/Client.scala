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
//  var channels = Map.empty[(ActorRef, ActorRef), (Symbol, Int)] // (from, to), (messageType, commitId)
//  val messageTypesMap = Map('CommitRequest -> "CoR", 'Prepare -> "Pre", 'Commit -> "Com", 'Agree -> "Agr",
//    'Abort -> "Abo", 'Ack -> "Ack", 'Other -> " # ")
//  val messageTypesColorsMap = Map('CommitRequest -> Console.YELLOW, 'Prepare -> Console.BLUE, 'Commit -> Console.GREEN,
//    'Agree -> Console.GREEN, 'Abort -> Console.RED, 'Ack -> Console.BLUE, 'Other -> Console.WHITE)
//  var states = Map.empty[ActorRef, (Symbol, Int)] // cohort, (state, commitId)
//  val statesMap = Map('pending -> "Q", 'waiting -> "W", 'prepared -> "P", 'commited -> "C", 'aborted -> "A")
//  val statesColorsMap = Map('pending -> Console.WHITE, 'waiting -> Console.YELLOW, 'prepared -> Console.BLUE,
//    'commited -> Console.GREEN, 'aborted -> Console.RED)
  val defaultConsoleInfoBarText = "Type >number [ENTER]< to cause node failure"
  var consoleInfoBar = defaultConsoleInfoBarText
  var consoleInfoBarSchedulerCancel: Cancellable = _

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive = {
    case Print(text) =>
      println(text)
//    case Print(actors, action, messageType, value, stateType) =>
//      action match {
//        case 'send =>
//          actors foreach { actor => channels += ((sender, actor) -> ((messageType, value))) }
//          states += (sender -> ((stateType, value)))
//        case 'got =>
//          actors foreach { actor => channels -= ((actor, sender)) }
//          states += (sender -> ((stateType, value)))
//      }
//      print("\n\n\n\n\n\n\n\n\n")
//      print("COORDINATOR:    ")
//      val (state, commitId) = states(coordinator)
//      println(s"0 ${statesColorsMap(state)}${statesMap(state)}${Console.RESET} $commitId")
//      1 to cohorts.size foreach { _ => print("↓↓↓ ↑↑↑    ") }
//      println()
//      cohorts foreach {
//        case (cohort, id) =>
//          List((coordinator, cohort), (cohort, coordinator)) foreach { // print both channels if there is message in channel
//            case (a, b) =>
//              channels.get((a,b)) match {
//                case Some((mType, cId)) =>
//                  print(messageTypesColorsMap(mType) + messageTypesMap(mType) + " " + Console.RESET)
//                case _ => print("    ")
//              }
//          }
//          print("   ")
//      }
//      println()
//      1 to cohorts.size foreach { _ => print("↓↓↓ ↑↑↑    ") }
//      println()
//      cohorts foreach {
//        case (cohort, id) =>
//          val (state, cId) = states(cohort)
//          print(f"$id%2d ${statesColorsMap(state)}${statesMap(state)}${Console.RESET} $cId%2d    ")
//      }
//      print(s"\n\n$consoleInfoBar \n")

    case Text(text) =>
      import context.dispatcher
      if (consoleInfoBarSchedulerCancel != null) {
        consoleInfoBarSchedulerCancel.cancel()
      }
      text match {
        case "" => consoleInfoBar = defaultConsoleInfoBarText
        case _ =>
          consoleInfoBar = text
          val requestor = self
          consoleInfoBarSchedulerCancel = context.system.scheduler.scheduleOnce(3000 milliseconds) {
            requestor ! Text("")
          }
      }

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
          currentNode ! Startup(dijkstraActors(currentNode), nextNode)
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
  def findActorById(nodes: Map[ActorRef, Int], id: Int) = {
    val result: Option[(ActorRef, Int)] = nodes.find(_._2 == id)
    result match {
      case Some((n, i)) =>
        Some(n)
      case _ =>
        None
    }
  }

  def unknownCommand(sendTo: ActorRef): Unit = {
    sendTo ! Text("UNKNOWN COMMAND")
  }

  def receive = {
    case Nodes(actors) =>
      while (true) {
        try {
          val c = scala.io.StdIn.readLine().split(" ").toList
//          c.head match {
//            case "f" => // fail
//              findActorById(allNodes, c(1).toInt) match {
//                case Some(n: ActorRef) =>
//                  n ! DoFail()
//                  sender ! Text("Failure of node " + allNodes(n))
//                case _ =>
//                  unknownCommand(sender)
//              }
//            case "a" => // abort
//              findActorById(allNodes, c(1).toInt) match {
//                case Some(n: ActorRef) =>
//                  n ! AbortNextTime()
//                  sender ! Text("Node " + allNodes(n) + " will abort next time")
//                case _ =>
//                  unknownCommand(sender)
//              }
//            case _ =>
//              unknownCommand(sender)
//          }
        } catch {
          case e: Exception =>
            unknownCommand(sender)
        }
      }
  }
}