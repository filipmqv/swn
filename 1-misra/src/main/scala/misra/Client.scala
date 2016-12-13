package misra

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
    system.actorOf(Props(classOf[ClientActor], "/user/statsService", args(0).toInt), "client")
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

  var nodes = Set.empty[Address]
  var nodesNumbersMap = Map.empty[ActorRef, Int]
  var channels = Map.empty[Symbol, (ActorRef, Int)]
  var possessions = Map.empty[Symbol, (ActorRef, Int)]
  var consoleInfoBar = ""

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive = {
    case Print(actor, symbol, value) =>
      symbol match {
        case 'sendping => possessions -= 'ping; channels += ('ping -> ((sender, value)))
        case 'sendpong => possessions -= 'pong; channels += ('pong -> ((sender, value)))
        case 'gotping => possessions += ('ping -> ((sender, value))); channels -= 'ping
        case 'gotpong => possessions += ('pong -> ((sender, value))); channels -= 'pong
        case 'errorping => println("###" + nodesNumbersMap(sender) + " got ERROR PING " + value + " from " + nodesNumbersMap(actor))
        case 'errorpong => println("###" + nodesNumbersMap(sender) + " got ERROR PONG " + value + " from " + nodesNumbersMap(actor))
      }
      print("\n\n\n\n\n\n\n\n\n      ")
      nodesNumbersMap foreach {
        case (_, -1) => ()
        case (k, v) =>
          print(f"$v%3d   ")
      }
      print("\nPING: ")
      nodesNumbersMap foreach {
        case (_, -1) => ()
        case (k, v) =>
          if (possessions.contains('ping) && possessions('ping)._1 == k) print(Console.RED + f"${possessions('ping)._2}%3d" + Console.RESET) else print("___")
          if (channels.contains('ping) && channels('ping)._1 == k) print(Console.RED + f"${channels('ping)._2}%3d" + Console.RESET) else print(">>>")
      }
      print("\nPONG: ")
      nodesNumbersMap foreach {
        case (_, -1) => ()
        case (k, v) =>
          if (possessions.contains('pong) && possessions('pong)._1 == k) print(Console.RED + f"${possessions('pong)._2}%3d" + Console.RESET) else print("___")
          if (channels.contains('pong) && channels('pong)._1 == k) print(Console.RED + f"${channels('pong)._2}%3d" + Console.RESET) else print(">>>")
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

    case MemberUp(m) if m.hasRole("compute") =>
      nodes += m.address
      if (nodes.size == clusterSize) {
        implicit val resolveTimeout = Timeout(5 seconds)
        // change adresses to ActorRefs
        nodes.zipWithIndex.foreach {
          case (node, i) => nodesNumbersMap +=
            (Await.result(context.actorSelection(RootActorPath(node) / servicePathElements)
            .resolveOne(), resolveTimeout.duration) -> i)
        }

        // tell actors about next nodes
        for (i <- 0 until nodesNumbersMap.size) {
          val currentNode = nodesNumbersMap.toIndexedSeq(i)._1
          val nextNode = nodesNumbersMap.toIndexedSeq((i+1)%nodesNumbersMap.size)._1
          currentNode ! Startup(i, nextNode)
        }

        // send Ping to first node
        val firstNode = nodesNumbersMap.toIndexedSeq(0)._1
        possessions += ('ping -> ((self, 1)))
        self ! Print(firstNode, 'sendping, 1)
        firstNode ! Ping(1)

        // send Pong to first node
        val fn = firstNode
        val s = self
        import context.dispatcher
        context.system.scheduler.scheduleOnce(3000 milliseconds) {
          possessions += ('pong -> ((s, -1)))
          s ! Print(fn, 'sendpong, -1)
          fn ! Pong(-1)
        }

        // send actorRefs to actor which handles losing ping or pong messages
        pingPongLoserActor ! Nodes(nodesNumbersMap)
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