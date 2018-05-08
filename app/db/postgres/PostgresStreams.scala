package db.postgres

import java.sql.{ SQLException, Statement }
import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.{ JsObject, JsValue, Json, __ }
import play.api.db.Database
import scala.collection.mutable.ListBuffer

import db.Streams
import db.Sensors
import models.StreamModel
import models.StreamModel._

/**
 * Store streams in Postgres.
 */
class PostgresStreams @Inject() (db: Database, sensors: Sensors) extends Streams {

  def createStream(stream: StreamModel): Int = {
    // connection will be closed at the end of the block
    db.withConnection { conn =>
      val ps = conn.prepareStatement("INSERT INTO streams(name, geog, created, metadata, sensor_id) " +
        "VALUES(?, CAST(ST_GeomFromGeoJSON(?) AS geography), NOW(), CAST(? AS json), ?);", Statement.RETURN_GENERATED_KEYS)

      // Set query parameters into proper positions in statement
      ps.setString(1, stream.name)
      ps.setString(2, Json.stringify(Json.toJson(stream.geometry)))
      ps.setString(3, Json.stringify(stream.properties))
      ps.setInt(4, stream.sensor_id)

      ps.executeUpdate()
      val rs = ps.getGeneratedKeys
      rs.next()
      val id = rs.getInt(1)
      rs.close()
      ps.close()
      id
    }
  }

  def getStream(id: Int): Option[StreamModel] = {
    db.withConnection { conn =>
      // TODO store start time, end time and parameter list in the row and update them when the update stream endpoint is called.
      // Then simplify this query to not calculate them on the fly.
      val query = "SELECT row_to_json(t,true) As my_stream FROM " +
        "(SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, sensor_id::int, " +
        "to_char(start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS start_time,to_char(end_time AT TIME " +
        "ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS end_time, params AS parameters FROM streams WHERE gid=?) As t;"

      // Set query parameters into proper positions in statement
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      val rs = st.executeQuery()

      var stream: Option[StreamModel] = None
      while (rs.next()) {
        val data = rs.getString(1)
        stream = Some(Json.parse(data).as[StreamModel])
      }
      rs.close()
      st.close()
      stream
    }
  }

  def getBinForStream(time: String, stream_id: Int) {
    db.withConnection { conn =>
      val query = "SELECT extract(year from start_time) as yyyy, avg(cast(data ->> \"'temperature\"' as double precision)) from datapoints where stream_id = " + stream_id +
        "  group by yyyy;"

      val st = conn.prepareStatement(query)
      st.setInt(1, stream_id)
      Logger.debug("Streams get statement: " + st)
      val rs = st.executeQuery()
      var streamData = ""
      while (rs.next()) {
        streamData += rs.getString(1)
      }
      rs.close()
      st.close()
      val asJson = Json.parse(streamData).as[JsObject]
      Logger.debug("Got as Json")

    }
  }

  def patchStreamMetadata(id: Int, data: String): Option[StreamModel] = {
    db.withConnection { conn =>
      val query = "SELECT row_to_json(t, true) AS my_stream FROM (" +
        "SELECT metadata As properties FROM streams " +
        "WHERE gid=?) AS t"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      Logger.debug("Streams get statement: " + st)
      val rs = st.executeQuery()
      var streamData = ""
      while (rs.next()) {
        streamData += rs.getString(1)
      }
      rs.close()
      st.close()

      val oldDataJson = Json.parse(streamData).as[JsObject]
      val newDataJson = Json.parse(data).as[JsObject]

      val jsonTransformer = (__ \ 'properties).json.update(
        __.read[JsObject].map { o => o ++ newDataJson }
      )
      val updatedJSON = oldDataJson.transform(jsonTransformer).getOrElse(oldDataJson)

      val query2 = "UPDATE streams SET metadata = CAST(? AS json) WHERE gid = ?"
      val st2 = conn.prepareStatement(query2)
      st2.setString(1, Json.stringify((updatedJSON \ "properties").getOrElse(Json.obj())))
      st2.setInt(2, id.toInt)
      Logger.debug("Stream put statement: " + st2)
      val rs2 = st2.executeUpdate()
      st2.close()
      getStream(id)
    }
  }

  def updateStreamStats(stream_id: Option[Int]) {
    db.withConnection { conn =>
      // always update the empty streams list first
      sensors.updateEmptyStats()

      // next update the streams
      var query = "UPDATE streams SET start_time=n.start_time, end_time=n.end_time, params=n.params FROM ("
      query += "  SELECT stream_id, min(datapoints.start_time) AS start_time, max(datapoints.end_time) AS end_time, " +
        "array_agg(distinct keys) AS params"
      query += "    FROM datapoints, jsonb_object_keys(data) data(keys)"
      if (stream_id.isDefined) query += " WHERE stream_id = ?"
      query += "    GROUP BY stream_id) n"
      query += "  WHERE n.stream_id=streams.gid;"

      val st = conn.prepareStatement(query)
      if (stream_id.isDefined) st.setInt(1, stream_id.get)
      Logger.debug("updateStreamStats statement: " + st)
      st.execute()
      st.close()
    }
  }

  def searchStreams(geocode: Option[String], stream_name: Option[String]): List[StreamModel] = {
    db.withConnection { conn =>
      val parts = geocode match {
        case Some(x) => x.split(",")
        case None => Array[String]()
      }
      var i = 0
      var query = "SELECT row_to_json(t,true) As my_places FROM " +
        "(SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, sensor_id::int, " +
        "to_char(start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS start_time, to_char(end_time AT TIME " +
        "ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS end_time, params AS parameters FROM streams"
      if (parts.length == 3) {
        query += " WHERE ST_DWithin(geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
        if (stream_name.isDefined) {
          query += " AND name = ?"
        }
      } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
        query += " WHERE ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
        i = 0
        while (i < parts.length) {
          query += "ST_MakePoint(?, ?), "
          i += 2
        }
        query += "ST_MakePoint(?, ?)])), geog)"
        if (stream_name.isDefined) {
          query += " AND name = ? "
        }
      } else if (parts.length == 0) {
        if (stream_name.isDefined) {
          query += " WHERE name = ? "
        }
      }
      query += ") As t;"
      val st = conn.prepareStatement(query)
      i = 0
      if (parts.length == 3) {
        st.setDouble(i + 1, parts(1).toDouble)
        st.setDouble(i + 2, parts(0).toDouble)
        st.setDouble(i + 3, parts(2).toDouble * 1000)
        if (stream_name.isDefined) {
          st.setString(i + 4, stream_name.getOrElse(""))
        }
      } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
        while (i < parts.length) {
          st.setDouble(i + 1, parts(i + 1).toDouble)
          st.setDouble(i + 2, parts(i).toDouble)
          i += 2
        }
        st.setDouble(i + 1, parts(1).toDouble)
        st.setDouble(i + 2, parts(0).toDouble)
        if (stream_name.isDefined) {
          st.setString(i + 3, stream_name.getOrElse(""))
        }
      } else if (parts.length == 0 && stream_name.isDefined) {
        st.setString(1, stream_name.getOrElse(""))
      }
      st.setFetchSize(50)
      Logger.debug("Stream search statement: " + st)
      val rs = st.executeQuery()

      var streams: ListBuffer[StreamModel] = ListBuffer()

      while (rs.next()) {
        val data = rs.getString(1)
        streams += Json.parse(data).as[StreamModel]
      }
      rs.close()
      st.close()
      streams.toList
    }
  }

  def deleteStream(id: Int): Unit = {
    db.withConnection { conn =>
      val deleteStream = "DELETE from datapoints where stream_id = ?" +
        "DELETE from streams where gid = ?;"

      val st = conn.prepareStatement(deleteStream)
      st.setInt(1, id)
      st.setInt(2, id)
      st.execute()
      st.close()
    }
  }
}
