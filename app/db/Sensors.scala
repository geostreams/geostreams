package db

import com.google.inject.ImplementedBy
import db.postgres.PostgresSensors
import model.SensorModel
import play.api.libs.json.{JsObject, JsValue}

/**
  * Access Sensors store.
  */
@ImplementedBy(classOf[PostgresSensors])
trait Sensors {
  def createSensor(sesor: SensorModel): Int
  def getSensor(id: Int): JsValue
  def updateSensorMetadata(id: Int , update: JsObject): JsValue
  def deleteSensor(id: Int): Unit
}
