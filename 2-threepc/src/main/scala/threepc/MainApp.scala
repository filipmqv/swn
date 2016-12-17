package threepc

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object MainApp {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      startup(Seq("0"), false)
    } else {
      startup(Seq("2551"), true) // 2 seed-nodes (1 coordinator, 1 cohort)
      startup(Seq("2552"), false)
      Client.main(args)
    }
  }

  def startup(ports: Seq[String], coordinator: Boolean): Unit = {
    ports foreach { port =>
      // Override the configuration of the port when specified as program argument
      val config =
        if (coordinator) {
          ConfigFactory.parseString(s"akka.remote.netty.tcp.port=" + port).withFallback(
            ConfigFactory.parseString("akka.cluster.roles = [coordinator]")).
            withFallback(ConfigFactory.load("threepc"))
        } else {
          ConfigFactory.parseString(s"akka.remote.netty.tcp.port=" + port).withFallback(
            ConfigFactory.parseString("akka.cluster.roles = [cohort]")).
            withFallback(ConfigFactory.load("threepc"))
        }

      val system = ActorSystem("ClusterSystem", config)

      if (coordinator) {
        system.actorOf(Props[Coordinator], name = "threepcService")
      } else {
        system.actorOf(Props[Cohort], name = "threepcService")
      }
    }
  }
}

