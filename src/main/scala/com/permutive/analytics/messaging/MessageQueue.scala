package com.permutive.analytics.messaging

/** Concurrency **/
import concurrent.Future, concurrent.ExecutionContext.Implicits.global
import akka.actor.Actor
import akka.pattern.pipe

trait MessageQueue[A] {
  def read: Future[Option[A]]
}

trait MessageQueueRequest
case object ReadMessage extends MessageQueueRequest

trait MessageQueueResponse
case class HandleMessage[A](message: A) extends MessageQueueResponse
case object EndMessage extends MessageQueueResponse

trait MessageQueueActor[A] extends Actor with MessageQueue[A] {
  def receive = {
    case ReadMessage =>
      read.map(_.fold(EndMessage: MessageQueueResponse)(HandleMessage(_))) pipeTo sender
  }
}
