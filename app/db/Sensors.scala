package db

import com.google.inject.ImplementedBy
import db.postgres.PostgresSensors
import models.{ SensorModel, StreamModel }
import play.api.libs.json.{ JsObject, JsValue }

/**
 * Access Sensors store.
 */
@ImplementedBy(classOf[PostgresSensors])
trait Sensors {
  def createSensor(sensor: SensorModel): Int
  def getSensor(id: Int): Option[SensorModel]
  def getSensorSources(id: Int, parameter: String): List[String]
  def updateSensorMetadata(id: Int, update: JsObject): JsValue
  def getSensorStats(id: Int): JsValue
  def getSensorStreams(id: Int): List[StreamModel]
  def updateSensorStats(id: Option[Int]): Unit
  def searchSensors(geocode: Option[String], sensor_name: Option[String]): List[SensorModel]
  def deleteSensor(id: Int): Unit
  // used in other db.
  def updateEmptyStats()
}
