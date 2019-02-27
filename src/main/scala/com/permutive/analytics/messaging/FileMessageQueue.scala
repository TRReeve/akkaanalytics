package com.permutive.analytics.messaging

/** Concurrency **/
import concurrent.Future, concurrent.ExecutionContext.Implicits.global
/** IO **/
import io.Source._
/** General **/
import util.Try

class FileMessageQueue(file: String) extends MessageQueueActor[String] {

  val lines = fromFile(file).getLines

  def read: Future[Option[String]] = {
    Future { Try(lines.next).toOption }
  }
}
