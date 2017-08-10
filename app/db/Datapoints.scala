package db

import com.google.inject.ImplementedBy
import db.postgres.PostgresDatapoints
import model.DatapointModel
import play.api.libs.json.{JsObject, JsValue}

/**
  * Access Datapoints store.
  */
@ImplementedBy(classOf[PostgresDatapoints])
trait Datapoints {
  def addDatapoint(datapoint: DatapointModel): Int
  def getDatapoint(id: Int): Option[DatapointModel]
  def searchDatapoints(since: Option[String], until: Option[String], geocode: Option[String], stream_id: Option[String], sensor_id: Option[String],
                       source: List[String], attributes: List[String], sortByStation: Boolean): Iterator[JsObject]
  def deleteDatapoint(id: Int): Unit
}
