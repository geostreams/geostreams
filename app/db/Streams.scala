package db

import com.google.inject.ImplementedBy
import db.postgres.PostgresStreams
import model.StreamModel
import play.api.libs.json.{JsObject, JsValue}

/**
  * Access Streams store.
  */
@ImplementedBy(classOf[PostgresStreams])
trait Streams {
  def createStream(stream: StreamModel): Int
  def getStream(id: Int): JsValue
  def patchStreamMetadata(id: Int, data: String): JsValue
  def updateStreamStats(stream_id: Option[Int])
  def searchStreams(geocode: Option[String], stream_name: Option[String]): Option[String]
  def deleteStream(id: Int): Unit
}
