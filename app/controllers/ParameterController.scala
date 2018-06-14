package controllers

import javax.inject.{ Inject, Singleton }

import com.mohiva.play.silhouette.api.Silhouette
import db.Parameters
import models.{ CategoryModel, CategoryParameterMapping, ParameterModel }
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.{ JsError, Json }
import play.api.mvc.Action
import utils.silhouette._

@Singleton
class ParameterController @Inject() (val silhouette: Silhouette[TokenEnv], val messagesApi: MessagesApi,
    parametersDB: Parameters) extends AuthTokenController with I18nSupport {

  /**
   * Gets all the parameters, mappings and categories in the database.
   *
   * @return a list with parameters, categories and mappings for all data in the database
   */
  def getParameters() = Action {
    val parameters = parametersDB.getAllParameters()
    val categories = parametersDB.getAllCategories()
    val mappings = parametersDB.getAllCategoryParameterMappings()
    Ok(Json.obj("status" -> "OK", "parameters" -> parameters, "categories" -> categories, "mappings" -> mappings))
  }

  /**
   * Deletes a parameter and mappings for that parameter given an id.
    *
   * @param id
   * @return
   */
  def deleteParameter(id: Int) = SecuredAction(WithService("master")) {
    parametersDB.deleteParameter(id)
    Ok(Json.obj("status" -> "OK", "message" -> "Deleted parameter"))
  }

  /**
   * Deletes a parameter and mappings for that parameter given a name
   *
   * @param name
   * @return
   */
  def deleteParameterByName(name: String) = SecuredAction(WithService("master")) {
    val parameter = parametersDB.getParameterByName(name)
    parameter match {
      case Some(p) => {
        parametersDB.deleteParameter(parameter.get.id)
        Ok(Json.obj("status" -> "OK", "message" -> "Deleted parameter"))
      }
      case None => {
        Ok(Json.obj("status" -> "KO", "message" -> "No existing parameter with the given name."))
      }
    }
  }

  /**
   * Adds or updates a parameter if it exist already by name
   * It requires a json input that includes "parameters" and "categories" as keys.
   * It returns the parameter, the mappings and associated categories.
   *
   * Workflow:
   * 1. Check if parameter is in database by name, if so:
   *   - Get all associated categories from mappings
   *   - For each category in the input, check if the category exist in database by name + type
   *   - If it exist, check if it is in the mapping. If it is don't do anything.
   *   - If the category is not in the mapping, add a mapping.
   *   - If the category doesn't exist, create a category and add the mapping.
   * 2. If the parameter is not in the database create one.
   *   - Check if the category exist by name and type.
   *   - If the category doesn't exist create the category.
   *   - Add a mapping (Since the parameter didn't exist before, the mapping doesn't exist either)
   *
   * @return the parameter, categories and mapping created in the database with id's
   */
  def addParameter() = SecuredAction(WithService("master"))(parse.json) { implicit request =>
    val categories = request.body.\("categories").validate[List[CategoryModel]]
    val parameter = request.body.\("parameter").validate[ParameterModel]

    parameter.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      parameter => {
        categories.fold(
          errors => {
            BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
          },
          categories => {
            val parameter_in_db = parametersDB.getParameterByName(parameter.name)
            if (parameter_in_db.isDefined) {
              parametersDB.updateParameter(parameter)
              var existing_mappings = parametersDB.getMappingByParameterId(parameter_in_db.get.id)
              var associated_categories: List[CategoryModel] = List.empty
              categories.map(category => {
                val category_in_db = parametersDB.getCategoryNameAndType(category.name, category.detail_type)
                category_in_db match {
                  case Some(c) => {
                    val mapping_exists = existing_mappings.filter(a => a.category_id == c.id)
                    if (mapping_exists.isEmpty) {
                      val new_mapping = new CategoryParameterMapping(parameter_id = parameter_in_db.get.id, category_id = c.id)
                      existing_mappings = existing_mappings :+ new_mapping
                    }
                    associated_categories = associated_categories :+ c
                  }
                  case None => {
                    val created_category = parametersDB.addCategory(category)
                    val new_mapping = new CategoryParameterMapping(parameter_id = parameter.id, category_id = created_category.id)
                    existing_mappings = existing_mappings :+ new_mapping
                    associated_categories = associated_categories :+ created_category
                  }
                }
              })
              Ok(Json.obj("status" -> "OK", "message" -> "parameter already existed", "parameter" -> parameter_in_db,
                "mappings" -> existing_mappings, "categories" -> associated_categories))
            } else {

              val created_parameter = parametersDB.createParameter(parameter)
              var associated_categories: List[CategoryModel] = List.empty
              var created_mappings: List[CategoryParameterMapping] = List.empty
              categories.map(category => {
                var category_id = 0
                val category_in_db = parametersDB.getCategoryNameAndType(category.name, category.detail_type)
                category_in_db match {
                  case Some(c) => {
                    category_id = c.id
                    associated_categories = associated_categories :+ c
                  }
                  case None => {
                    val created_category = parametersDB.addCategory(category)
                    category_id = created_category.id
                    associated_categories = associated_categories :+ created_category
                  }
                }
                val mapping = new CategoryParameterMapping(parameter_id = created_parameter.id, category_id = category_id)
                val created_mapping = parametersDB.addMapping(mapping)
                created_mappings = created_mappings :+ created_mapping

              })

              Ok(Json.obj("status" -> "OK", "parameter" -> created_parameter, "categories" -> associated_categories,
                "mappings" -> created_mappings))
            }
          }
        )
      }
    )

  }

}
