package db.postgres

import java.sql.{ SQLException, Statement }
import javax.inject.Inject
import play.api.Logger
import scala.collection.mutable.ListBuffer
import com.mohiva.play.silhouette.password.BCryptPasswordHasher

import db.Users
import models.User
import play.api.libs.json.{ JsObject, JsValue, Json, __ }
import play.api.db.Database
import play.api.libs.json._
import play.api.libs.json.Json._

/**
 * Store Users in Postgres.
 */
class PostgresUsers @Inject() (db: Database) extends Users {

  def get(id: Int): Option[User] = {
    var user: Option[User] = None
    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) AS my_user FROM (SELECT gid AS id, email, emailconfirmed, password, " +
        "first_name, last_name, organization, string_to_array(services, ',') AS services FROM users WHERE gid = ?) AS t"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        user = Some(Json.parse(data).as[User])
      }
      rs.close()
      st.close()
    }
    user
  }

  def findByEmail(email: String): Option[User] = {
    var user: Option[User] = None
    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) AS my_user FROM (SELECT gid AS id, email, emailconfirmed, password, " +
        "first_name, last_name, organization, string_to_array(services, ',') AS services FROM users WHERE email = ?) AS t"
      val st = conn.prepareStatement(query)
      st.setString(1, email)
      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        user = Some(Json.parse(data).as[User])
      }
      rs.close()
      st.close()
    }
    user
  }

  def save(user: User): User = {
    var theUser: Option[User] = None
    db.withConnection { conn =>
      var query = ""
      if (user.id.isDefined) {
        query = "UPDATE users SET email = ?, emailConfirmed = ?, password = ?, first_name = ?, last_name = ?, " +
          "organization = ?, services = ? WHERE gid = ?"
      } else {
        query = "INSERT INTO users(email, emailConfirmed,  password, first_name, last_name, organization, services) " +
          "VALUES(?, ?, ?, ?, ?, ?, ?) "
      }
      val st = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)
      st.setString(1, user.email)
      st.setBoolean(2, user.emailconfirmed)
      st.setString(3, user.password)
      st.setString(4, user.first_name)
      st.setString(5, user.last_name)
      st.setString(6, user.organization)
      st.setString(7, user.services.mkString(","))
      if (user.id.isDefined) {
        st.setInt(8, user.id.get)
      }

      st.executeUpdate()
      val rs = st.getGeneratedKeys
      var id = 0
      while (rs.next()) {
        id = rs.getInt(1)
      }

      theUser = Some(user.copy(id = Some(id)))
      rs.close()
      st.close()

      theUser.get
    }
  }

  def remove(email: String): Unit = {
    db.withConnection { conn =>
      var query = "DELETE FROM users WHERE email = ?"
      val st = conn.prepareStatement(query)
      st.setString(1, email)
      val rs = st.executeQuery()
      rs.close()
      st.close()
    }
  }

  def listAll(): List[User] = {
    var users: ListBuffer[User] = ListBuffer()
    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) AS my_user FROM (SELECT gid AS id, email, emailconfirmed, password, first_name, last_name, organization, " +
        "string_to_array(services, ',') AS services FROM users) AS t"
      val st = conn.prepareStatement(query)
      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        users += Json.parse(data).as[User]
      }
      rs.close()
      st.close()
    }
    users.toList
  }
}