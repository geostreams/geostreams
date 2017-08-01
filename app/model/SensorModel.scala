package model

import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.functional.syntax._

/**
  * Sensor model
  */
case class SensorModel(name: String, geoType: String, geometry: GeometryModel, properties: JsValue)

case class GeometryModel(`type`: String, coordinates: JsValue)

object GeometryModel{
  implicit val geometryReads: Reads[GeometryModel] = (
    (JsPath \ "type").read[String] and
      (JsPath \ "coordinates").read[JsValue]
    ) (GeometryModel.apply _)

  implicit val geometryWrite = Json.writes[GeometryModel]
}

object SensorModel {

  implicit val sensorReads: Reads[SensorModel] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "geometry").read[GeometryModel] and
      (JsPath \ 'properties).read[JsValue]
    ) (SensorModel.apply _)

  implicit val sensorWrite = Json.writes[SensorModel]
}