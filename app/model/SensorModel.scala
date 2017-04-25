package model

import play.api.libs.json.{JsValue, __}

/**
  * Created by lmarini on 4/25/17.
  */
case class SensorModel(name: String, geoType: String, geometry: GeometryModel, properties: JsValue)

case class GeometryModel(`type`: String, coordinates: List[Double])

object SensorModel {

}
