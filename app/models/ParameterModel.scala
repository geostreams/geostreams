package models

import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
  * Parameters are types of data stored in datapoints for which there is data associated.
  * This elements provides additional information about the parameter than the short name in the datapoints
  * It provides a longer title, the unit, information to show up on the search and explore views, names and colors for
  * stacked-bar graphs that could be used for other stacked graphs.
  *
  * @param id
  * @param name - Needs to be unique. A short version of the title
  * @param title - title for the parameter
  * @param unit - Unit for the data
  * @param search_view - boolean indicates whether the parameter will show up as a parameter in the search page
  * @param explore_view - boolean indicates whether the parameter will show up in the popup on the explore page
  * @param scale_names [Optional] Used for representing category names in stacked-bar graphs
  * @param scale_colors [Optional] Used for representing category colors in stacked-bar graphs
  */
case class ParameterModel(
  id: Int = 0,
  name: String,
  title: String,
  unit: String,
  search_view: Boolean,
  explore_view: Boolean,
  scale_names: List[String],
  scale_colors: List[String]
)

object ParameterModel {

  implicit val parameterReads: Reads[ParameterModel] = (
    ((JsPath \ "id").read[Int] or Reads.pure(0)) and
    (JsPath \ "name").read[String] and
    (JsPath \ "title").read[String] and
    (JsPath \ "unit").read[String] and
    (JsPath \ "search_view").read[Boolean] and
    (JsPath \ "explore_view").read[Boolean] and
    ((JsPath \ "scale_names").read[List[String]] or Reads.pure(List(): List[String])) and
    ((JsPath \ "scale_colors").read[List[String]] or Reads.pure(List(): List[String]))

  )(ParameterModel.apply _)

  implicit val parameterWrite = Json.writes[ParameterModel]

}