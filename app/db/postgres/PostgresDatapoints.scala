package db.postgres

import java.sql.{SQLException, Statement}
import javax.inject.Inject
import play.api.Logger

import db.Datapoints
import model.DatapointModel
import play.api.libs.json.{JsObject, JsValue, Json, __}
import play.api.db.Database
import model.DatapointModel._
import java.text.SimpleDateFormat
import play.api.libs.json._
import play.api.libs.json.Json._
import java.sql.Timestamp

/**
  * Store datapoints in Postgres.
  */
class PostgresDatapoints @Inject()(db: Database) extends Datapoints {
  def addDatapoint(datapoint: DatapointModel): Int = {
    db.withConnection { conn =>
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
      var failedToParse: Option[JsError] = None;

      val ps = conn.prepareStatement("INSERT INTO datapoints(start_time, end_time, stream_id, data, geog, created) " +
        "VALUES(?, ?, ?, CAST(? AS jsonb), CAST(ST_GeomFromGeoJSON(?) AS geography), NOW());", Statement.RETURN_GENERATED_KEYS)

      // Set query parameters into proper positions in statement
      val start = new Timestamp(formatter.parse(datapoint.start_time).getTime())
      ps.setTimestamp(1, new Timestamp(start.getTime))
      if (datapoint.end_time.isDefined) {
        val end = new Timestamp(formatter.parse(datapoint.end_time.get).getTime())
        ps.setTimestamp(2, new Timestamp(end.getTime))
      }
      else
        ps.setDate(2, null)

      ps.setInt(3, datapoint.stream_id)
      ps.setString(4, Json.stringify(datapoint.properties))
      ps.setString(5, Json.stringify(Json.toJson(datapoint.geometry)))

      // Execute query and get results
      ps.executeUpdate()
      val rs = ps.getGeneratedKeys
      rs.next()
      val id = rs.getInt(1)
      rs.close()
      ps.close()
      id
    }
  }

  def getDatapoint(id: Int): String = {
    db.withConnection { conn =>
      var data = ""
      val query = "SELECT row_to_json(t,true) As my_datapoint FROM " +
        "(SELECT datapoints.gid As id, " +
        "to_char(datapoints.created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "to_char(datapoints.start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS start_time, " +
        "to_char(datapoints.end_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS end_time, " +
        "datapoints.data As properties, 'Feature' As type, " +
        "ST_AsGeoJson(1, datapoints.geog, 15, 0)::json As geometry, " +
        "stream_id::text, sensor_id::text, sensors.name as sensor_name " +
        "FROM sensors, streams, datapoints " +
        "WHERE datapoints.gid=? AND sensors.gid = streams.sensor_id AND datapoints.stream_id = streams.gid) As t;"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      Logger.debug("Datapoints get statement: " + st)
      val rs = st.executeQuery()
      while (rs.next()) {
        data += rs.getString(1)
      }
      rs.close()
      st.close()
      data
    }
  }

  def deleteDatapoint(id: Int): Unit = {
    db.withConnection { conn =>
      val query = "DELETE FROM datapoints where gid = ?"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      Logger.debug("Deleting datapoint statement: " + st)
      st.execute()
      st.close
    }
  }

}
