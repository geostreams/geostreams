package db.postgres

import java.sql.Statement
import javax.inject.Inject

import db.Parameters
import models.{ CategoryModel, CategoryParameterMapping, ParameterModel }
import play.api.db.Database
import play.api.libs.json.Json

import scala.collection.mutable.ListBuffer

class PostgresParameters @Inject() (db: Database) extends Parameters {

  /**
   * Creates a parameter in the database.
   *
   * @param parameter
   * @return the created parameter that includes the generated id
   */
  override def createParameter(parameter: ParameterModel): ParameterModel = {
    var created_parameter: Option[ParameterModel] = None
    db.withConnection { conn =>

      val query = "INSERT into parameters(name, title, unit, search_view, explore_view," +
        "scale_names, scale_colors) VALUES (?, ?, ?, ?, ?, ?, ?) "

      val st = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
      st.setString(1, parameter.name)
      st.setString(2, parameter.title)
      st.setString(3, parameter.unit)
      st.setBoolean(4, parameter.search_view)
      st.setBoolean(5, parameter.explore_view)
      st.setString(6, parameter.scale_names.mkString(", "))
      st.setString(7, parameter.scale_colors.mkString(", "))

      st.executeUpdate()
      val rs = st.getGeneratedKeys
      var id = 0
      while (rs.next()) {
        id = rs.getInt(1)
      }

      created_parameter = Some(parameter.copy(id = id))
      rs.close()
      st.close()
      created_parameter.get
    }

  }

  /**
   * Updates all fields of a parameter given a model
   *
   * @param parameter
   */
  override def updateParameter(parameter: ParameterModel): Unit = {
    var updated_parameter: Option[ParameterModel] = None
    db.withConnection { conn =>

      val query = "UPDATE parameters SET title=?, unit=?, search_view=?, explore_view=?, scale_names=?, scale_colors=?" +
        "  WHERE name = ?"
      val st = conn.prepareStatement(query)
      st.setString(1, parameter.title)
      st.setString(2, parameter.unit)
      st.setBoolean(3, parameter.search_view)
      st.setBoolean(4, parameter.explore_view)
      st.setString(5, parameter.scale_names.mkString(", "))
      st.setString(6, parameter.scale_colors.mkString(", "))
      st.setString(7, parameter.name)
      st.executeUpdate()
      st.close()
    }

  }

  /**
   * Deletes all paraeter_categories mappings in the database given an id and then
   * removes the parameter from parameters table
   *
   * @param id of the parameter
   */
  override def deleteParameter(id: Int): Unit = {
    db.withConnection { conn =>

      val query = "DELETE FROM parameter_categories WHERE parameter_gid = ?;" +
        "DELETE FROM parameters WHERE gid = ?"

      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      st.setInt(2, id)
      st.execute()
      st.close
    }
  }

  /**
   * Finds a parameter given the short name
   *
   * @param name
   * @return the parameter if found in the database, None if it is not found
   */
  override def getParameterByName(name: String): Option[ParameterModel] = {
    var parameter: Option[ParameterModel] = None
    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) as my_parameter FROM ( SELECT gid AS id, name, title, unit," +
        "search_view, explore_view, string_to_array(scale_names, ', ') AS scale_names, " +
        "string_to_array(scale_colors, ', ') AS scale_colors FROM parameters WHERE name = ?) AS t"

      val st = conn.prepareStatement(query)
      st.setString(1, name)
      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        parameter = Some(Json.parse(data).as[ParameterModel])
      }
      rs.close()
      st.close()

    }
    parameter
  }

  /**
   * Gets a parameter given its id
   *
   * @param id
   * @return  Some(ParameterModel) if it is found, None if it isn't.
   */
  override def getParameter(id: Int): Option[ParameterModel] = {
    var parameter: Option[ParameterModel] = None
    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) as my_parameter FROM ( SELECT gid AS id, name, title, unit," +
        "search_view, explore_view, string_to_array(scale_names, ', ') AS scale_names, " +
        "string_to_array(scale_colors, ', ') AS scale_colors FROM parameters WHERE gid = ?) AS t"

      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        parameter = Some(Json.parse(data).as[ParameterModel])
      }
      rs.close()
      st.close()

    }
    parameter
  }

  /**
   * Gets all parameters available in the database
   *
   * @return all parameters in the parameters table.
   */
  override def getAllParameters(): List[ParameterModel] = {
    var parameters: ListBuffer[ParameterModel] = ListBuffer()

    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) as my_parameter FROM ( SELECT gid AS id, name, title, unit," +
        "search_view, explore_view, string_to_array(scale_names, ', ') AS scale_names, " +
        "string_to_array(scale_colors, ', ') AS scale_colors FROM parameters) AS t"

      val st = conn.prepareStatement(query)
      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        parameters += Json.parse(data).as[ParameterModel]
      }
      rs.close()
      st.close()
    }
    parameters.toList
  }

  override def getParametersByDetailType(detail_type: String): List[ParameterModel] = {

    var parameters: ListBuffer[ParameterModel] = ListBuffer()

    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) as my_parameter FROM ( SELECT parameters.gid AS id, " +
        "parameters.name, parameters.title, parameters.unit, " +
        "parameters.search_view, parameters.explore_view, string_to_array(parameters.scale_names, ', ') AS scale_names, " +
        "string_to_array(parameters.scale_colors, ', ') AS scale_colors FROM parameters, parameter_categories, categories " +
        "WHERE parameters.gid = parameter_categories.parameter_gid AND " +
        "categories.gid = parameter_categories.category_gid AND categories.detail_type = ? ) AS t"

      val st = conn.prepareStatement(query)
      st.setString(1, detail_type)
      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        parameters += Json.parse(data).as[ParameterModel]
      }
      rs.close()
      st.close()
    }
    parameters.toList
  }
  /**
   * Finds a category by name and type
   *
   * @param name of the category
   * @param detail_type of the category
   * @return Some[CategoryModel] if found, None if it isn't
   */
  override def getCategoryNameAndType(name: String, detail_type: String): Option[CategoryModel] = {
    var category: Option[CategoryModel] = None
    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) as my_category FROM ( SELECT gid AS id, name, detail_type " +
        "FROM categories WHERE name= ? and detail_type = ?) AS t "

      val st = conn.prepareStatement(query)
      st.setString(1, name)
      st.setString(2, detail_type)

      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        category = Some(Json.parse(data).as[CategoryModel])
      }
      rs.close()
      st.close()

    }
    category
  }

  /**
   * Adds a category to the database
   *
   * @param category
   * @return The newly created category including the id
   */
  override def addCategory(category: CategoryModel): CategoryModel = {
    var created_category: Option[CategoryModel] = None

    db.withConnection { conn =>
      val query = "INSERT into categories(name, detail_type) VALUES (?, ?) "

      val st = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
      st.setString(1, category.name)
      st.setString(2, category.detail_type)

      st.executeUpdate()
      val rs = st.getGeneratedKeys
      var id = 0
      while (rs.next()) {
        id = rs.getInt(1)
      }

      created_category = Some(category.copy(id = id))
      rs.close()
      st.close()
      created_category.get
    }
  }

  /**
   * Gets all categories in the categories table in the database
   *
   * @return a List of Category Models.
   */
  override def getAllCategories(): List[CategoryModel] = {
    var categories: ListBuffer[CategoryModel] = ListBuffer()

    db.withConnection { conn =>

      val query = "SELECT row_to_json(t, true) as my_category FROM ( SELECT gid AS id, name, detail_type " +
        "FROM categories ) AS t "

      val st = conn.prepareStatement(query)
      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        categories += Json.parse(data).as[CategoryModel]
      }
    }

    categories.toList

  }

  /**
   * Adds a mapping between a parameter and a category
   *
   * @param mapping
   * @return The newly created mapping with id
   */
  override def addMapping(mapping: CategoryParameterMapping): CategoryParameterMapping = {
    var created_mapping: Option[CategoryParameterMapping] = None
    db.withConnection { conn =>

      val query = "INSERT into parameter_categories(parameter_gid, category_gid) VALUES (?, ?)"

      val st = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
      st.setInt(1, mapping.parameter_id)
      st.setInt(2, mapping.category_id)

      st.executeUpdate()
      val rs = st.getGeneratedKeys
      var id = 0
      while (rs.next()) {
        id = rs.getInt(1)
      }

      created_mapping = Some(mapping.copy(id = id))
      rs.close()
      st.close()
      created_mapping.get
    }
  }

  /**
   * Gets all mappings associated with a parameter Id
   *
   * @param parameterId
   * @return a List of mappings for the given parameter Id
   */
  override def getMappingByParameterId(parameterId: Int): List[CategoryParameterMapping] = {
    var mappings: ListBuffer[CategoryParameterMapping] = ListBuffer()

    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) as my_mapping FROM (SELECT gid AS id, parameter_gid AS parameter_id," +
        " category_gid AS category_id FROM parameter_categories WHERE parameter_gid = ?) AS t "

      val st = conn.prepareStatement(query)
      st.setInt(1, parameterId)
      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        mappings += Json.parse(data).as[CategoryParameterMapping]
      }
    }
    mappings.toList
  }

  /**
   * Gets all category parameter mappings
   *
   * @return The list of all the mappings in parameter_categories table in the database
   */
  override def getAllCategoryParameterMappings(): List[CategoryParameterMapping] = {
    var mappings: ListBuffer[CategoryParameterMapping] = ListBuffer()

    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) as my_mapping FROM (SELECT gid AS id, parameter_gid AS parameter_id," +
        " category_gid AS category_id FROM parameter_categories) AS t"

      val st = conn.prepareStatement(query)
      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        mappings += Json.parse(data).as[CategoryParameterMapping]
      }
    }
    mappings.toList
  }

}
