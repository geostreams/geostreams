package db.postgres

import java.sql.{SQLException, Statement}
import javax.inject.Inject
import play.api.Logger

import db.Streams
import model.StreamModel
import play.api.libs.json.{JsObject, JsValue, Json, __}
import play.api.db.Database
import model.StreamModel._

/**
  * Store streams in Postgres.
  */
class PostgresStreams @Inject()(db: Database) extends Streams {

  def createStream(stream: StreamModel): Int = {
    // connection will be closed at the end of the block
    db.withConnection { conn =>
      val ps = conn.prepareStatement("INSERT INTO streams(name, geog, created, metadata) " +
        "VALUES(?, CAST(ST_GeomFromGeoJSON(?) AS geography), NOW(), CAST(? AS json));",
        Statement.RETURN_GENERATED_KEYS)
      ps.setString(1, stream.name)
      ps.setString(2, Json.stringify(Json.toJson(stream.geometry)))
      ps.setString(3, Json.stringify(stream.properties))


      val ps = conn.prepareStatement("INSERT INTO streams(name, geog, created, metadata, sensor_id) " +
        "VALUES(?, CAST(ST_GeomFromGeoJSON(?) AS geography), NOW(), CAST(? AS json), ?);", Statement.RETURN_GENERATED_KEYS)

      // Set query parameters into proper positions in statement
      ps.setString(1, stream.name)
      ps.setString(2, Json.stringify(geojson))
      ps.setString(3, metadata)
      ps.setInt(4, stream_id.toInt)

      ps.executeUpdate()
      val rs = ps.getGeneratedKeys
      rs.next()
      val id = rs.getInt(1)
      rs.close()
      ps.close()
      id
    }
  }

  def getStream(id: Int): JsValue = {
    db.withConnection { conn =>
      // TODO store start time, end time and parameter list in the row and update them when the update stream endpoint is called.
      // Then simplify this query to not calculate them on the fly.
      val query = "WITH stream_info AS (" +
        "SELECT stream_id, start_time, end_time, unnest(params) AS param FROM streams WHERE stream_id=?)" +
        "SELECT row_to_json(t, true) AS my_stream FROM (" +
        "SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, " +
        "to_char(min(stream_info.start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS min_start_time, " +
        "to_char(max(stream_info.end_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') as max_end_time, " +
        "array_agg(distinct stream_info.param) as parameters " +
        "FROM streams " +
        "LEFT OUTER JOIN stream_info ON stream_info.stream_id = streams.gid " +
        "WHERE streams.gid=?" +
        "GROUP BY gid) AS t"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      st.setInt(2, id)
      val rs = st.executeQuery()
      rs.next()
      val data = rs.getString(1)
      rs.close()
      st.close()
      Json.parse(data)
    }
  }

  def updateStreamMetadata(id: Int , update: JsObject): JsValue = {
    // connection will be closed at the end of the block
    db.withTransaction { conn =>
      try {
        val query = "SELECT row_to_json(t, true) AS my_stream FROM (" +
          "SELECT metadata As properties FROM streams " +
          "WHERE gid=?) AS t"
        val st = conn.prepareStatement(query)
        st.setInt(1, id.toInt)
        val rs = st.executeQuery()
        var streamData = ""
        while (rs.next()) {
          streamData += rs.getString(1)
        }
        rs.close()
        st.close()

        val oldDataJson = Json.parse(streamData).as[JsObject]

        val jsonTransformer = (__ \ 'properties).json.update(
          __.read[JsObject].map { o => o ++ update }
        )
        val updatedJSON: JsObject = oldDataJson.transform(jsonTransformer).getOrElse(oldDataJson)

        val query2 = "UPDATE streams SET metadata = CAST(? AS json) WHERE gid = ?"
        val st2 = conn.prepareStatement(query2)
        st2.setString(1, Json.stringify((updatedJSON \ "properties").getOrElse(Json.obj())))
        st2.setInt(2, id)
        st2.executeUpdate()
        st2.close()
        conn.commit()
        return updatedJSON
      } catch {
        case e: SQLException => {
          conn.rollback()
          // TODO make sure this is proper, try -> catch -> rollback -> throw
          throw e
        }
      }
    }
  }



  def deleteStream(id: Int): Unit = {
    db.withConnection { conn =>
      val query = "DELETE FROM datapoints USING streams WHERE stream_id IN (SELECT gid FROM streams WHERE stream_id = ?);" +
        "DELETE FROM streams WHERE gid IN (SELECT gid FROM streams WHERE stream_id = ?);" +
        "DELETE FROM streams where gid = ?;"
      val st = conn.prepareStatement(query)
      st.setInt(1, id.toInt)
      st.setInt(2, id.toInt)
      st.setInt(3, id.toInt)
      st.executeUpdate()
      st.close()
    }
  }
}
