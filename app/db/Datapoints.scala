package db

import java.sql.Timestamp

import com.google.inject.ImplementedBy
import db.postgres.PostgresDatapoints
import models.DatapointModel
import play.api.libs.json.{ JsArray, JsObject, JsValue }

/**
 * Access Datapoints store.
 */
@ImplementedBy(classOf[PostgresDatapoints])
trait Datapoints {
  def addDatapoint(datapoint: DatapointModel): Int
  def addDatapoints(datapoints: List[DatapointModel]): Int
  def getDatapoint(id: Int): Option[DatapointModel]
  def searchDatapoints(since: Option[String], until: Option[String], geocode: Option[String], stream_id: Option[String], sensor_id: Option[String],
    source: List[String], attributes: List[String], sortByStation: Boolean): List[JsObject]
  def deleteDatapoint(id: Int): Unit
  def renameParam(oldParam: String, newParam: String, source: Option[String], region: Option[String])
  def getCount(sensor_id: Option[Int]): Int
}
