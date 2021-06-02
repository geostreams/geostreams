package db

import com.google.inject.ImplementedBy
import db.postgres.PostgresParameters
import models.{ CategoryModel, CategoryParameterMapping, ParameterModel }

/**
 * Access Parameter Store. Implementations are in /db/postgres/PostgresParameters
 */
@ImplementedBy(classOf[PostgresParameters])
trait Parameters {

  def createParameter(parameter: ParameterModel): ParameterModel
  def getParameter(id: Int): Option[ParameterModel]
  def getParameterByName(name: String): Option[ParameterModel]
  def getAllParameters(): List[ParameterModel]
  def deleteParameter(id: Int): Unit
  def updateParameter(parameter: ParameterModel): Unit
  def getParametersByDetailType(detail_type: String): List[ParameterModel]

  def addCategory(category: CategoryModel): CategoryModel
  def getCategoryNameAndType(name: String, detail_type: String): Option[CategoryModel]
  def getAllCategories(): List[CategoryModel]

  def getAllCategoryParameterMappings(): List[CategoryParameterMapping]
  def addMapping(mapping: CategoryParameterMapping): CategoryParameterMapping
  def getMappingByParameterId(parameterId: Int): List[CategoryParameterMapping]

  def isParameterNested(name: String): Boolean

}
