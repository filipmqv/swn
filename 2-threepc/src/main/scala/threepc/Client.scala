package threepc

import akka.actor.{Actor, ActorRef, ActorSystem, Address, Props, RelativeActorPath, RootActorPath}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.language.postfixOps

object Client {
  def main(args: Array[String]): Unit = {
    // note that client is not a compute node, role not defined
    val system = ActorSystem("ClusterSystem")
    system.actorOf(Props(classOf[ClientActor], "/user/threepcService", args(0).toInt), "client")
  }
}

class ClientActor(servicePath: String, clusterSize: Int) extends Actor {
  val cluster = Cluster(context.system)
  val servicePathElements = servicePath match {
    case RelativeActorPath(elements) => elements
    case _ => throw new IllegalArgumentException(
      "servicePath [%s] is not a valid relative actor path" format servicePath)
  }
  val pingPongLoserActor = cluster.system.actorOf(Props[PingPongLoserActor])

  var nodeIndex = 0
  var nodes = Set.empty[Address]
  var cohorts = Map.empty[ActorRef, Int]
  var coordinator: ActorRef = _
  var channels = Map.empty[(ActorRef, ActorRef), (Symbol, Int)] // (from, to), (messageType, commitId)
  val messageTypesMap = Map('CommitRequest -> "CoR", 'Prepare -> "Pre", 'Commit -> "Com", 'Agree -> "Agr",
    'Abort -> "Abo", 'Ack -> "Ack")
  val messageTypesColorsMap = Map('CommitRequest -> Console.YELLOW, 'Prepare -> Console.BLUE, 'Commit -> Console.GREEN,
    'Agree -> Console.GREEN, 'Abort -> Console.RED, 'Ack -> Console.BLUE)
  var states = Map.empty[ActorRef, (Symbol, Int)] // cohort, (state, commitId)
  val statesMap = Map('pending -> "Q", 'waiting -> "W", 'prepared -> "P", 'commited -> "C", 'aborted -> "A")
  var consoleInfoBar = ""

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive = {
    case Print(actors, action, messageType, value, stateType) =>
      action match {
        case 'send =>
          actors foreach { actor => channels += ((sender, actor) -> ((messageType, value))) }
          states += (sender -> ((stateType, value)))
        case 'got =>
          actors foreach { actor => channels -= ((actor, sender)) }
          states += (sender -> ((stateType, value)))
      }
      print("\n\n\n\n\n\n\n\n\n")
      print("COORDINATOR:    ")
      val (state, commitId) = states(coordinator)
      println(statesMap(state) + " " + commitId)
      1 to cohorts.size foreach { i => print("↓↓↓ ↑↑↑    ") }
      println()
      cohorts foreach {
        case (cohort, id) =>
          List((coordinator, cohort), (cohort, coordinator)) foreach { // print both channels if there is message in channel
            case (a, b) =>
              channels.get((a,b)) match {
                case Some((mType, cId)) =>
                  print(messageTypesColorsMap(mType) + messageTypesMap(mType) + " " + Console.RESET)
                case _ => print("    ")
              }
          }
          print("   ")
      }
      println()
      1 to cohorts.size foreach { i => print("↓↓↓ ↑↑↑    ") }
      println()
      cohorts foreach {
        case (cohort, id) =>
          val (state, cId) = states(cohort)
          print(statesMap(state) + " " + f"$commitId%3d" + "      ")
      }
      print(s"\n\n$consoleInfoBar \n")

    case Text(text) =>
      import context.dispatcher
      text match {
        case "" => consoleInfoBar = ""
        case _ =>
          consoleInfoBar = text
          val requestor = self
          context.system.scheduler.scheduleOnce(3000 milliseconds) {
            requestor ! Text("")
          }
      }

    case MemberUp(m) if m.hasRole("coordinator") =>
      nodes += m.address
      implicit val resolveTimeout = Timeout(5 seconds)
      coordinator = Await.result(context.actorSelection(RootActorPath(m.address) / servicePathElements)
        .resolveOne(), resolveTimeout.duration)

    case MemberUp(m) if m.hasRole("cohort") =>
      nodes += m.address
      implicit val resolveTimeout = Timeout(5 seconds)
      nodeIndex += 1
      cohorts += (Await.result(context.actorSelection(RootActorPath(m.address) / servicePathElements)
        .resolveOne(), resolveTimeout.duration) -> nodeIndex)

      if (nodes.size == clusterSize) {
        // tell coordinator about cohorts
        coordinator ! StartupCoordinator(cohorts)
        states += (coordinator -> (('pending, 0)))
        cohorts foreach {
          case (cohort, id) =>
            cohort ! StartupCohort(id)
            states += (cohort -> (('pending, 0)))
        }
        // TODO send actorRefs to actor which handles failures or timeouts
        //pingPongLoserActor ! Nodes(cohorts)
      }

    case other: MemberEvent                         => nodes -= other.member.address
    case UnreachableMember(m)                       => nodes -= m.address
    case ReachableMember(m) if m.hasRole("compute") => nodes += m.address
  }

}

class PingPongLoserActor extends Actor {
  def receive = {
    case Nodes(nodesMap) =>
      while (true) {
        val c = scala.io.StdIn.readLine()
        // i - lose ping, o - lose pong
        c match {
          case "i" =>
            val n = nodesMap.toIndexedSeq(ThreadLocalRandom.current.nextInt(nodesMap.size))._1
            n ! LoseMessage('ping)
            sender ! Text(s"### channel before node ${nodesMap(n)} will lose PING")
          case "o" =>
            val n = nodesMap.toIndexedSeq(ThreadLocalRandom.current.nextInt(nodesMap.size))._1
            n ! LoseMessage('pong)
            sender ! Text(s"### channel before node ${nodesMap(n)} will lose PONG")
          case _ => sender ! Text("### COMMAND UNKNOWN")
        }
      }
  }
}