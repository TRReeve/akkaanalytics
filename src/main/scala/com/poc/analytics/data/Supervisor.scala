package main.scala.com.poc.analytics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.poc.analytics.messaging._
import ApiFormats._
import spray.json._

import scala.concurrent.duration._

class IORouter(system: ActorSystem, messagestack: ActorRef) extends Actor {


  /**
    * Actor for constructing Operations from Received Messages
    * and routing users to corresponding actor
    */
  implicit val timeout = Timeout(5 seconds)

  var actordocumentmap = scala.collection.mutable.Map[String, ActorRef]()
  var userdocumentmap = scala.collection.mutable.Map[String, String]()
  var orphanedmessagesstack = scala.collection.mutable.Map[String, UpdateMessage]()

  def receive: Receive = {

    case "tick" => println("Procesing...")

    //ping message stack with read message
    case ReadMessage => messagestack ! ReadMessage

    // convert Handle Messages from Message Queue IO to our types
    case HandleMessage(message) => {

      //HandleMessage -> SprayJsonObject -> Map[String, JSValue]
      val contenttomap = message.toString.parseJson.asJsObject.fields

      //case match based on string representation of messageType
      contenttomap("messageType").convertTo[String] match {

        case "VisitUpdate" => {
          val jsonupdatemessage = contenttomap("visit").asJsObject.convertTo[UpdateMessage]
          ioupdateactor(jsonupdatemessage)
        }
        case "VisitCreate" => {
          val jsontocreatemessage = contenttomap("visit").asJsObject.convertTo[CreateMessage]
          iocreateactor(jsontocreatemessage.documentId, jsontocreatemessage)
          val checkorphans = orphanedmessagesstack.getOrElse(jsontocreatemessage.id, 0)

          if (checkorphans != 0) {
            println("new create message, orphan found in map")
            ioupdateactor(checkorphans.asInstanceOf[UpdateMessage])
          }
        }
      }
    }

    case kpiRequest: KpiRequest => {
      val numberofnodes = actordocumentmap.size
      println(s"Requesting Data for $numberofnodes DocumentNodes")
      actordocumentmap.values.foreach(_ ! kpiRequest)
    }

    case OrphanFlush => {

      if (orphanedmessagesstack.isEmpty == false) {

        println("Beginning Orphan Flush")

        //"current time" variable for testing purposes (current time being when the logs were run)
        val currenttime = orphanedmessagesstack.maxBy(_._2.updatedAt.toString)._2.updatedAt

        //turn flush bufffer into stack of all orphans within last hour
        val orphanflushbuffer = orphanedmessagesstack.filter(_._2.updatedAt.isAfter(currenttime.minusSeconds(30))).toIterator

        val deprecatedmessages = orphanedmessagesstack.filterNot(_._2.updatedAt.isAfter(currenttime.minusSeconds(30)))

        println(orphanflushbuffer.size + " update messages being retried to find a home")

        println(deprecatedmessages.size + " update messages deprecated due to age")

        //empty stack of remaining messages, could also be dumped to another system/storage etc.
        deprecatedmessages.map(k => orphanedmessagesstack.remove(k._1))

        //flush orphaned update messages through system again, if no home is found then back to the orphaned messages stack
        for (elem <- orphanflushbuffer) ioupdateactor(elem._2)

      }


    }
  }

  def iocreateactor(docid: String, message: CreateMessage): Unit = {

    /**
      * Check for an actor reference corresponding to the document and/or create
      *
      * If already exists then message is sent to the corresponding actor for processing
      * Else a new actor is created with an actor address mapped to the document id.
      */

    if (actordocumentmap.exists(_._1 == docid)) {

      val targetactor = actordocumentmap.get(message.documentId)
      targetactor.get ! message
      userdocumentmap += (message.id -> message.documentId)
    }

    else if (actordocumentmap.exists(_._1 == docid) == false) {

      val newdocumentactor = system.actorOf(Props(new DocumentNode(docid, self)))
      actordocumentmap += (docid -> newdocumentactor)
      newdocumentactor ! message
    }
  }

  def ioupdateactor(message: UpdateMessage): Unit = {

    /**
      * Check for a visit router for the user id
      *
      * If not found then added to orphaned messages stack
      * for visits with an update but no associated document
      */

    if (userdocumentmap.exists(_._1 == message.id)) {

      val actor = actordocumentmap(userdocumentmap(message.id))
      actor ! message

      if (orphanedmessagesstack.exists(_._1 == message.id)) {

        //check existence in orphaned messages stack and remove if present
        orphanedmessagesstack.remove(message.id)
      }

    }
    else {

      //no visit router found, send to orphan stack to be flushed later
      orphanedmessagesstack += (message.id -> message)
    }
  }
}