package models

import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 * Sensor model. The default field will be created by database
 */
case class SensorModel(
  id: Int = 0,
  name: String,
  created: String = "N/A",
  geoType: String,
  geometry: GeometryModel,
  properties: JsValue,
  min_start_time: String,
  max_end_time: String,
  parameters: List[String] = List()
)

case class GeometryModel(`type`: String, coordinates: JsValue)

object GeometryModel {
  implicit val geometryReads: Reads[GeometryModel] = (
    (JsPath \ "type").read[String] and
    (JsPath \ "coordinates").read[JsValue]
  )(GeometryModel.apply _)

  implicit val geometryWrite = Json.writes[GeometryModel]
}

object SensorModel {

  implicit val sensorReads: Reads[SensorModel] = (
    ((JsPath \ "id").read[Int] or Reads.pure(0)) and
    (JsPath \ "name").read[String] and
    ((JsPath \ "created").read[String] or Reads.pure("N/A")) and
    (JsPath \ "type").read[String] and
    (JsPath \ "geometry").read[GeometryModel] and
    (JsPath \ "properties").read[JsValue] and
    ((JsPath \ "min_start_time").read[String] or Reads.pure("N/A")) and
    ((JsPath \ "max_end_time").read[String] or Reads.pure("N/A")) and
    ((JsPath \ "parameters").read[List[String]] or Reads.pure(List(): List[String]))

  )(SensorModel.apply _)

  implicit val sensorWrite = Json.writes[SensorModel]
}
