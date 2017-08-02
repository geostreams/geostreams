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
  def getDatapoint(id: Int): String
  def deleteDatapoint(id: Int): Unit
}
