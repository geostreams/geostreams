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
  def getStream(id: Int): Option[StreamModel]
  def patchStreamMetadata(id: Int, data: String): Option[StreamModel]
  def updateStreamStats(stream_id: Option[Int])
  def searchStreams(geocode: Option[String], stream_name: Option[String]): List[StreamModel]
  def deleteStream(id: Int): Unit
}
