package db.postgres

import db.Events
import models.User
import play.api.db.Database
import java.sql.{ SQLException, Statement }
import javax.inject.Inject

import com.mohiva.play.silhouette.api.Identity
import play.api.libs.json.{ JsValue, Json }

import scala.collection.mutable.ListBuffer

class PostgresEvents @Inject() (db: Database) extends Events {

  def save(userid: Option[Int], request: Map[String, Seq[String]]) = {
    db.withConnection { conn =>
      if (userid.isDefined) {
        var query = "INSERT INTO events(userid, sources, attributes, geocode, since, until, purpose, date) " +
          "VALUES(?, ?, ?, ?, ?, ?, ?, now())"

        val st = conn.prepareStatement(query)
        st.setInt(1, userid.get)
        st.setString(2, request.get("sources").map(_.mkString(", ")).getOrElse("--"))
        st.setString(3, request.get("attributes").map(_.mkString(", ")).getOrElse("--"))
        st.setString(4, request.get("geocode").map(_.head).getOrElse("--"))
        st.setString(5, request.get("since").map(_.head).getOrElse("--"))
        st.setString(6, request.get("until").map(_.head).getOrElse("--"))
        st.setString(7, request.get("purpose").map(_.head).getOrElse("--").slice(0, 999))

        st.executeUpdate()
        val rs = st.getGeneratedKeys
        var id = 0
        while (rs.next()) {
          id = rs.getInt(1)
        }
        rs.close()
        st.close()
      }
    }
  }

  //TODO: add filter when we have more kinds of events.
  def listAll(): List[JsValue] = {
    var events: ListBuffer[JsValue] = ListBuffer()
    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) AS my_user FROM (SELECT users.email AS user, date, sources, attributes, geocode, since, until, purpose FROM events, users WHERE users.gid = events.userid) AS t"
      val st = conn.prepareStatement(query)
      val rs = st.executeQuery()

      while (rs.next()) {
        val data = rs.getString(1)
        events += Json.parse(data)
      }
      rs.close()
      st.close()
    }
    events.toList
  }

}
