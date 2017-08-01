package model

import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.functional.syntax._
import model.GeometryModel

/**
  * Stream model.
  */
case class StreamModel(name: String, geoType: String, geometry: GeometryModel, properties: JsValue)

object StreamModel {
//  implicit val geometryWrite = Json.writes[GeometryModel]

  implicit val streamReads: Reads[StreamModel] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "geometry").read[GeometryModel] and
      (JsPath \ "properties").read[JsValue]
    ) (StreamModel.apply _)

  implicit val streamWrite = Json.writes[StreamModel]
}
