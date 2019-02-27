package main.scala.com.permutive.analytics.data

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import spray.json._


object ApiFormats extends DefaultJsonProtocol {

  implicit val updatemessage = jsonFormat4(UpdateMessage)
  implicit val createmessage = jsonFormat4(CreateMessage)
  implicit val reportmessage = jsonFormat7(AnalyticsReport)
  implicit object DateTimeFormat extends RootJsonFormat[DateTime] {


    // quick fix for code changed formatter from .dateTimenoMillis to .datetime() so everything takes same time format as logs
    val formatter = ISODateTimeFormat.dateTime()

    def write(obj: DateTime): JsValue = {
      JsString(formatter.print(obj))
    }

    def read(json: JsValue): DateTime = json match {
      case JsString(s) => try {
        formatter.parseDateTime(s)
      }
      catch {
        case t: Throwable => error(s)
      }
      case _ =>
        error(json.toString())
    }

    def error(v: Any): DateTime = {
      val example = formatter.print(0)
      deserializationError(f"'$v' is not a valid date value. Dates must be in compact ISO-8601 format, e.g. '$example'")
    }
  }


}

//case class to receive requests for Ids for routing.
case class LocationNotification(id: String, document: String)

//case class for orphaned update message
case class OrphanedMessage(updateMessage: UpdateMessage)

//case classes for Deserializing two types of message
case class UpdateMessage(id: String, engagedTime: Long, completion: Float, updatedAt: DateTime)

case class CreateMessage(id: String, userId: String, documentId: String, createdAt: DateTime)

trait FlushRequest

case object OrphanFlush extends FlushRequest

//object for Analytics report
case class KpiRequest(starttime: DateTime, endtime: DateTime)

case class AnalyticsReport(documentid: String, starttime: DateTime, endtime: DateTime, visits: Long, uniqueusers: Long, hours: Float, completed: Long)