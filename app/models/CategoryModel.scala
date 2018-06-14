package models

import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
  * Categories are used to represent the type of graphs to show in the detail page in Geodashboard v3
  * The name will be the title of the tab where the data associated with the category is displayed
  * The detail_type is the type of graph in the UI, it could be (time, stacked-line, stacked-bar)
  *
  * @param id
  * @param name - to show as title of a tab in the detail page
  * @param detail_type - determines the type of graph to show in the UI
  */
case class CategoryModel(
  id: Int = 0,
  name: String,
  detail_type: String
)

object CategoryModel {

  implicit val configurationReads: Reads[CategoryModel] = (
    ((JsPath \ "id").read[Int] or Reads.pure(0)) and
    (JsPath \ "name").read[String] and
    (JsPath \ "detail_type").read[String]
  )(CategoryModel.apply _)

  implicit val configurationWrite = Json.writes[CategoryModel]
}

