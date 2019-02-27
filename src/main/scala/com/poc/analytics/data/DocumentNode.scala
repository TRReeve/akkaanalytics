package main.scala.com.poc.analytics.data

import akka.actor.{Actor, ActorRef}
import main.scala.com.poc.analytics.data.ApiFormats
import org.joda.time
import org.joda.time.DateTime
import spray.json._

class DocumentNode(actordocumentid: String, supervisor: ActorRef) extends Actor {

  /** Actor To Handle a document entities data and respond to requests from IO Router
    * for if a userid is present.
    *
    * Aggregates data and returns in a data object for
    */


  var createdvisits = scala.collection.mutable.HashMap[String, CreateMessage]()
  var updatedvisits = scala.collection.mutable.HashMap[String, UpdateMessage]()

  def receive: Receive = {

    case updateMessage: UpdateMessage => {
      val found = ioupdatecheckinsert(updateMessage)

      //notify Supervisor of location
      sender() ! new LocationNotification(updateMessage.id, actordocumentid)

    }

    case createMessage: CreateMessage => {
      val userid = createMessage.userId
      val documentid = createMessage.documentId
      createdvisits += (createMessage.id -> createMessage)

    }

    case kpiRequest: KpiRequest => {

      val functionstart = new DateTime()

      //run report and convert the report Object to JSON
      val reportjson = generatereport(kpiRequest).toJson

      //times processing time of report, variable could also be used for back pressuring.
      val functionend = new time.Period(functionstart, new DateTime())

      //export function (prints to stdout for now)
      val export = ioexportdata(reportjson)

      //print to stdout
      println("Kpi Report For document %s for time period from %s and %s\n-- Processed In: %f milliseconds --\n%s\n"
        .format(actordocumentid, kpiRequest.starttime, kpiRequest.endtime, functionend.getMillis.toDouble, export))
    }
  }

  def ioupdatecheckinsert(message: UpdateMessage): Boolean = {
    /**
      * takes update message and checks for created visit
      */

    val visitid = message.id

    //returns to function caller if an update operation happened
    var updated = false
    val newupdate = message.updatedAt

    if (createdvisits.exists(_._1 == message.id)) {

      if (updatedvisits.exists(_._1 == message.id == true)) {
        val mostrecenttime = updatedvisits(message.id).updatedAt

        if (message.updatedAt.isAfter(mostrecenttime)) {
          //println(s"overwriting update message of $newupdate over $mostrecenttime")

          //check time from new message is more recent than the existing one before update
          assert(message.updatedAt.isAfter(mostrecenttime))

          updatedvisits += (message.id -> message)
          updated = true
        }
      }
      else {
        updatedvisits += (message.id -> message)
        updated = true

      }
    }
    else {
      //if no created message found then send back to supervisor
      println("Orphaned Message discovered on document node")
      supervisor ! OrphanedMessage(message)
    }
    updated
  }

  def generatereport(kpiRequest: KpiRequest): AnalyticsReport = {

    /**
      * Takes start and end time of kpi request and filters down data collection, calculates kpis
      * for requested fields and returns as Analytics Report Object
      **/

    val filtercreatedvisits = for (x <- createdvisits
                                   if x._2.createdAt.isAfter(kpiRequest.starttime);
                                   if x._2.createdAt.isBefore(kpiRequest.endtime)) yield x

    //use that filtered list to get updates information
    val filterupdatedvisits = filtercreatedvisits.map(k => k._1 -> updatedvisits.get(k._1))
      .collect { case (key, Some(value)) => (key, value) }

    val numbervisits = filtercreatedvisits.values.groupBy(_.id).size
    val uniqueusers = filtercreatedvisits.values.groupBy(_.userId).size
    val sumseconds = filterupdatedvisits.values.toIterator.map(_.engagedTime).filter(_ > 0).sum / 3600.toFloat
    val completedreads = filterupdatedvisits.values.filter(_.completion == 1).size

    //generate report from kpi calculations
    new AnalyticsReport(actordocumentid, kpiRequest.starttime, kpiRequest.endtime, numbervisits, uniqueusers, sumseconds, completedreads)
  }

  def ioexportdata(data: JsValue): String = {

    /*
    * Stub Function for export/dbinsert operation etc
    * */

    data.compactPrint
  }
}
