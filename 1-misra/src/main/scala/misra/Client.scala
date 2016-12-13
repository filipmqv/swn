package misra

import akka.actor.{Actor, ActorRef, ActorSystem, Address, Props, RelativeActorPath, RootActorPath}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, MemberStatus}
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

object Client {
  def main(args: Array[String]): Unit = {
    // note that client is not a compute node, role not defined
    val system = ActorSystem("ClusterSystem")
    system.actorOf(Props(classOf[ClientActor], "/user/statsService"), "client")
  }
}

class ClientActor(servicePath: String) extends Actor {
  val cluster = Cluster(context.system)
  val servicePathElements = servicePath match {
    case RelativeActorPath(elements) => elements
    case _ => throw new IllegalArgumentException(
      "servicePath [%s] is not a valid relative actor path" format servicePath)
  }

  var nodes = Set.empty[Address]
  var nodesNumbersMap = Map.empty[ActorRef, Int]
  var channels = Map.empty[Symbol, (ActorRef, Int)]
  var possessions = Map.empty[Symbol, (ActorRef, Int)]

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent], classOf[ReachabilityEvent])
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def receive = {
    case Print(actor, symbol, value) =>
      symbol match {
        case 'sendping => possessions -= 'ping; channels += ('ping -> (sender, value))
        case 'sendpong => possessions -= 'pong; channels += ('pong -> (sender, value))
        case 'gotping => possessions += ('ping -> (sender, value)); channels -= 'ping
        case 'gotpong => possessions += ('pong -> (sender, value)); channels -= 'pong
        case 'errorping => println("###" + nodesNumbersMap(sender) + " got ERROR PING " + value + " from " + nodesNumbersMap(actor))
        case 'errorpong => println("###" + nodesNumbersMap(sender) + " got ERROR PONG " + value + " from " + nodesNumbersMap(actor))
      }
      print("\n\n\n\n\n\n\n\n\nPING: ")
      nodesNumbersMap foreach {
        case (_, -1) => val a = 1
        case (k, v) =>
          if (possessions.contains('ping) && possessions('ping)._1 == k) print(Console.RED + f"${possessions('ping)._2}%2d" + Console.RESET) else print("__")
          if (channels.contains('ping) && channels('ping)._1 == k) print(Console.RED + f"${channels('ping)._2}%2d" + Console.RESET) else print(">>")
      }
      print("\nPONG: ")
      nodesNumbersMap foreach {
        case (_, -1) => val a = 1
        case (k, v) =>
          if (possessions.contains('pong) && possessions('pong)._1 == k) print(Console.RED + f"${possessions('pong)._2}%2d" + Console.RESET) else print("__")
          if (channels.contains('pong) && channels('pong)._1 == k) print(Console.RED + f"${channels('pong)._2}%2d" + Console.RESET) else print(">>")
      }
      print("\n")
//    case state: CurrentClusterState =>
//      nodes = state.members.collect {
//        case m if m.hasRole("compute") && m.status == MemberStatus.Up => m.address
//      }
    case MemberUp(m) if m.hasRole("compute") => {
      nodes += m.address
      // TODO number of nodes to args + config from args (min number of nodes)
      if (nodes.size == 7) {
        implicit val resolveTimeout = Timeout(5 seconds)
        nodes.zipWithIndex.foreach {
          case (node, i) => nodesNumbersMap +=
            (Await.result(context.actorSelection(RootActorPath(node) / servicePathElements)
            .resolveOne(), resolveTimeout.duration) -> i)
        }
        for (i <- 0 until nodesNumbersMap.size) {
          val currentNode = nodesNumbersMap.toIndexedSeq(i)._1
          val nextNode = nodesNumbersMap.toIndexedSeq((i+1)%nodesNumbersMap.size)._1
          currentNode ! Startup(i, nextNode)
        }
        val firstNode = nodesNumbersMap.toIndexedSeq(0)._1
        nodesNumbersMap += (self -> -1)
        possessions += ('ping -> (self, 1))
        self ! Print(firstNode, 'sendping, 1)
        firstNode ! Ping(1)
        Thread sleep 1500

        possessions += ('pong -> (self, -1))
        self ! Print(firstNode, 'sendpong, -1)
        firstNode ! Pong(-1)
      }

    }
    case other: MemberEvent                         => nodes -= other.member.address
    case UnreachableMember(m)                       => nodes -= m.address
    case ReachableMember(m) if m.hasRole("compute") => nodes += m.address
  }

}
