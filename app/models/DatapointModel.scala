package models

import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.functional.syntax._

/**
 * Datapoint model. The default field will be created by database
 */
case class DatapointModel(start_time: String, end_time: Option[String], geoType: String, geometry: GeometryModel,
  properties: JsValue, stream_id: Int, sensor_id: Int = 0, sensor_name: String = "N/A")

object DatapointModel {

  implicit val datapointReads: Reads[DatapointModel] = (
    (JsPath \ "start_time").read[String] and
    (JsPath \ "end_time").readNullable[String] and
    (JsPath \ "type").read[String] and
    (JsPath \ "geometry").read[GeometryModel] and
    (JsPath \ "properties").read[JsValue] and
    (JsPath \ "stream_id").read[Int] and
    ((JsPath \ "sensor_id").read[Int] or Reads.pure(0)) and
    ((JsPath \ "sensor_name").read[String] or Reads.pure("N/A"))

  )(DatapointModel.apply _)

  implicit val datapointWrite = Json.writes[DatapointModel]
}
