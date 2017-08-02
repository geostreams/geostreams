package model

import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.functional.syntax._

/**
  * Datapoint model
  */
case class DatapointModel(start_time: String, end_time: Option[String], geoType: String, geometry: GeometryModel,
                          properties: JsValue, stream_id: Int, sensor_id: Int)

object DatapointModel {

  implicit val datapointReads: Reads[DatapointModel] = (
      (JsPath \ "start_time").read[String] and
      (JsPath \ "end_time").readNullable[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "geometry").read[GeometryModel] and
      (JsPath \ "properties").read[JsValue] and
      (JsPath \ "stream_id").read[Int] and
      (JsPath \ "sensor_id").read[Int]

    ) (DatapointModel.apply _)

  implicit val datapointWrite = Json.writes[DatapointModel]
}
