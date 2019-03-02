package com.poc.analytics

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import com.poc.analytics.messaging._
import main.scala.com.poc.analytics.{IORouter,KpiRequest,OrphanFlush}
import org.joda.time.DateTime

import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory





object Main {

  val conf = ConfigFactory.load()

  implicit val system = ActorSystem("akka-analytics")
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(5 seconds)

  def main(args: Array[String]): Unit = {

    /**
      * Initialises actors and system scheduler for Async Actors.
      **/

    val readfrequency = 5
    val flushorphans = 30
    val reportsschedule = 10
    val messages_location = "test-visit-messages.log"

    //using dummy time of when the log starts
    val current_time = new DateTime("2015-05-18T23:55:49.033Z").toDateTimeISO

    val messagestack = system.actorOf(Props(new FileMessageQueue(messages_location)))
    println(s"Initialised Message Actor @ $messagestack")

    val supervisor = system.actorOf(Props(new IORouter(system, messagestack)))
    println(s"Initialised Supervisor Actor @ $supervisor")



    val request = KpiRequest(current_time.hourOfDay().roundFloorCopy(), new DateTime().hourOfDay().roundCeilingCopy())

    // ping supervisor with ReadMessage command every 5 milliseconds to read message stack
    system.scheduler.schedule(0 milliseconds,readfrequency milliseconds, supervisor, ReadMessage)

    // ping Supervisor to re-flush orphans queue
    system.scheduler.schedule(5 seconds,flushorphans seconds, supervisor, OrphanFlush)

    // ping Supervisor to request KPIs report
    system.scheduler.schedule(5 seconds, reportsschedule seconds, supervisor, request)

    system.scheduler.schedule(0 seconds, 2 seconds, supervisor, "tick")

    while (true) {

    }

    println("Shutting Down")
    system.shutdown()

  }
}
