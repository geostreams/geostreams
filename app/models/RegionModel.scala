package models

import play.api.libs.json.{ JsPath, JsValue, Json, Reads }
import play.api.libs.functional.syntax._

/**
 * Region model. The default field will be created by database
 */
case class RegionModel(
  `type`: String,
  geometry: GeometryModel,
  properties: JsValue
)

object RegionModel {

  implicit val regionReads: Reads[RegionModel] = (
    (JsPath \ "type").read[String] and
    (JsPath \ "geometry").read[GeometryModel] and
    (JsPath \ "properties").read[JsValue]
  )(RegionModel.apply _)

  implicit val regionWrite = Json.writes[RegionModel]
}
