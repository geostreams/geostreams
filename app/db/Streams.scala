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
  def updateStreamMetadata(id: Int, update: JsObject): JsValue
  def deleteStream(id: Int): Unit
}
