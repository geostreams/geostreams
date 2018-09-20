package models

import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 * Mappings between a parameter and a category
 * To add a new mapping, the category and parameter should both exist
 * All mappings associated with a parameter are deleted upon parameter deletion.
 *
 * This is used for allowing the same parameter to show up on more than one tab or with different representations
 * For example, alkalinity can be in both a stacked-line graph and a time series graph
 * or Alkalinity could show up as time series in two categories/tabs: Contaminants and Nutrients
 *
 * @param id
 * @param parameter_id - must exist in the database, foreign key to parameter table id
 * @param category_id - must exist in the database, foreign key to category table id
 */
case class CategoryParameterMapping(
  id: Int = 0,
  parameter_id: Int,
  category_id: Int
)

object CategoryParameterMapping {
  implicit val categoryParameterMappingReads: Reads[CategoryParameterMapping] = (
    ((JsPath \ "id").read[Int] or Reads.pure(0)) and
    ((JsPath \ "parameter_id").read[Int] or Reads.pure(0)) and
    ((JsPath \ "category_id").read[Int] or Reads.pure(0))
  )(CategoryParameterMapping.apply _)

  implicit val categoryParameterMappingWrites = Json.writes[CategoryParameterMapping]
}
