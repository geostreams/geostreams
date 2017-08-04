package model

import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.functional.syntax._
import model.GeometryModel

/**
  * Stream model. The default field will be created by database
  */
case class StreamModel(id:Int = 0,
                       name: String,
                       created: String = "N/A",
                       geoType: String,
                       geometry: GeometryModel,
                       properties: JsValue,
                       sensor_id:Int,
                       start_time: String,
                       end_time: String,
                       parameters: List[String] = List())

object StreamModel {

  implicit val streamReads: Reads[StreamModel] = (
        ((JsPath \ "id").read[Int] or Reads.pure(0)) and
        (JsPath \ "name").read[String] and
        ((JsPath \ "created").read[String] or Reads.pure("N/A")) and
        (JsPath \ "type").read[String] and
        (JsPath \ "geometry").read[GeometryModel] and
        (JsPath \ "properties").read[JsValue] and
        (JsPath \ "sensor_id").read[Int] and
        ((JsPath \ "start_time").read[String]  or Reads.pure("N/A")) and
        ((JsPath \ "end_time").read[String]  or Reads.pure("N/A")) and
        ((JsPath \ "parameters").read[List[String]] or Reads.pure(List():List[String]))
    ) (StreamModel.apply _)

  implicit val streamWrite = Json.writes[StreamModel]
}
