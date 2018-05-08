package db

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
  def searchDatapointsByBin(since: Option[String], until: Option[String], geocode: Option[String], stream_id: Option[String], sensor_id: Option[String],
    source: List[String], attributes: List[String], sortByStation: Boolean, time: String): List[JsObject]
  def trendsByRegion(attribute: String, geocode: String): List[JsValue]
  def deleteDatapoint(id: Int): Unit
  def renameParam(oldParam: String, newParam: String, source: Option[String], region: Option[String])
}
