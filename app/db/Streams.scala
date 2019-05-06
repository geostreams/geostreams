package db

import com.google.inject.ImplementedBy
import db.postgres.PostgresStreams
import models.StreamModel
import play.api.libs.json.{ JsObject, JsValue }

/**
 * Access Streams store.
 */
@ImplementedBy(classOf[PostgresStreams])
trait Streams {
  def createStream(stream: StreamModel): Int
  def getStream(id: Int): Option[StreamModel]
  def patchStreamMetadata(id: Int, data: String): Option[StreamModel]
  def updateStreamStats(stream_id: Option[Int])
  def searchStreams(geocode: Option[String] = None, stream_name: Option[String] = None): List[StreamModel]
  def getBinForStream(time: String, stream_id: Int): List[JsValue]
  def deleteStream(id: Int): Unit
  def deleteStreams(start: Int, end: Int): Unit
  def getCount(sensor_id: Option[Int]): Int
}
