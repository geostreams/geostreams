package db.postgres

import java.sql.{ SQLException, Statement }

import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.{ JsObject, JsValue, Json, __ }
import play.api.db.Database
import play.api.libs.json._
import play.api.libs.json.Json._

import scala.collection.mutable.ListBuffer
import db.Sensors
import models.{ SensorModel, StreamModel }
import models.SensorModel._

/**
 * Store sensors in Postgres.
 */
class PostgresSensors @Inject() (db: Database) extends Sensors {

  def createSensor(sensor: SensorModel): Int = {
    // connection will be closed at the end of the block
    db.withConnection { conn =>
      val ps = conn.prepareStatement(
        "INSERT INTO sensors(name, geog, created, metadata) " +
          "VALUES(?, CAST(ST_GeomFromGeoJSON(?) AS geography), NOW(), CAST(? AS json));",
        Statement.RETURN_GENERATED_KEYS
      )
      ps.setString(1, sensor.name)
      ps.setString(2, Json.stringify(Json.toJson(sensor.geometry)))
      ps.setString(3, Json.stringify(sensor.properties))
      ps.executeUpdate()
      val rs = ps.getGeneratedKeys
      rs.next()
      val id = rs.getInt(1)
      rs.close()
      ps.close()
      id
    }
  }

  // TODO: Should these be included in the bin cache?
  // TODO: Commented this method to check if it improves performance. Make sure to add alternate way to return sources for sensor
  def getSensorSources(id: Int, parameter: String): List[String] = {
    //    db.withConnection { conn =>
    //      val query = "SELECT distinct(datapoints.data->>'source') FROM datapoints, streams where " +
    //        " datapoints.stream_id = streams.gid AND streams.sensor_id = ? AND datapoints.data ?? ?"
    //      val st = conn.prepareStatement(query)
    //      st.setInt(1, id)
    //      st.setString(2, parameter)
    //      val rs = st.executeQuery()
    //      var sources: ListBuffer[String] = ListBuffer.empty[String]
    //      while (rs.next()) {
    //        val data = rs.getString(1)
    //        sources += data
    //      }
    //      rs.close()
    //      st.close()
    //      sources.toList
    //    }
    List.empty
  }

  def getSensor(id: Int): Option[SensorModel] = {
    db.withConnection { conn =>
      // TODO store start time, end time and parameter list in the row and update them when the update sensor endpoint is called.
      // Then simplify this query to not calculate them on the fly.
      val query = "WITH stream_info AS (" +
        "SELECT sensor_id, start_time, end_time, unnest(params) AS param FROM streams WHERE sensor_id=?)" +
        "SELECT row_to_json(t, true) AS my_sensor FROM (" +
        "SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "'Feature' As type, metadata As properties, ST_AsGeoJson(geog, 15, 0)::json As geometry, " +
        "to_char(min(stream_info.start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS min_start_time, " +
        "to_char(max(stream_info.end_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') as max_end_time, " +
        "array_agg(distinct stream_info.param) as parameters " +
        "FROM sensors " +
        "LEFT OUTER JOIN stream_info ON stream_info.sensor_id = sensors.gid " +
        "WHERE sensors.gid=? " +
        "GROUP BY gid) AS t"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      st.setInt(2, id)
      val rs = st.executeQuery()
      var sensor: Option[SensorModel] = None
      while (rs.next()) {
        val data = rs.getString(1)
        sensor = Some(Json.parse(data).as[SensorModel])
      }
      rs.close()
      st.close()
      sensor
    }
  }

  def getSensors(ids: Array[Int]): List[SensorModel] = {
    db.withConnection { conn =>
      val idsArray = conn.createArrayOf("NUMERIC", ids.toArray[Any].asInstanceOf[Array[AnyRef]])
      // TODO store start time, end time and parameter list in the row and update them when the update sensor endpoint is called.
      // Then simplify this query to not calculate them on the fly.
      val query = "WITH stream_info AS (" +
        "SELECT sensor_id, start_time, end_time, unnest(params) AS param FROM streams WHERE sensor_id = ANY(?))" +
        "SELECT row_to_json(t, true) AS my_sensor FROM (" +
        "SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "'Feature' As type, metadata As properties, ST_AsGeoJson(geog, 15, 0)::json As geometry, " +
        "to_char(min(stream_info.start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS min_start_time, " +
        "to_char(max(stream_info.end_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') as max_end_time, " +
        "array_agg(distinct stream_info.param) as parameters " +
        "FROM sensors " +
        "LEFT OUTER JOIN stream_info ON stream_info.sensor_id = sensors.gid " +
        "WHERE sensors.gid = ANY(?) " +
        "GROUP BY gid) AS t"
      val st = conn.prepareStatement(query)
      st.setArray(1, idsArray)
      st.setArray(2, idsArray)
      val rs = st.executeQuery()
      val sensors = ListBuffer.empty[SensorModel]
      while (rs.next()) {
        val data = rs.getString(1)
        sensors += Json.parse(data).as[SensorModel]
      }
      rs.close()
      st.close()
      sensors.toList
    }
  }

  def updateSensorMetadata(id: Int, update: JsObject): JsValue = {
    // connection will be closed at the end of the block
    db.withTransaction { conn =>
      try {
        val query = "SELECT row_to_json(t, true) AS my_sensor FROM (" +
          "SELECT metadata As properties FROM sensors " +
          "WHERE gid=?) AS t"
        val st = conn.prepareStatement(query)
        st.setInt(1, id.toInt)
        val rs = st.executeQuery()
        var sensorData = ""
        while (rs.next()) {
          sensorData += rs.getString(1)
        }
        rs.close()
        st.close()

        val oldDataJson = Json.parse(sensorData).as[JsObject]

        val jsonTransformer = (__ \ 'properties).json.update(
          __.read[JsObject].map { o => o ++ update }
        )
        val updatedJSON: JsObject = oldDataJson.transform(jsonTransformer).getOrElse(oldDataJson)

        val query2 = "UPDATE sensors SET metadata = CAST(? AS json) WHERE gid = ?"
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

  def getSensorStats(id: Int): JsValue = {
    db.withConnection { conn =>
      val query = "WITH stream_info AS (" +
        "SELECT sensor_id, start_time, end_time, unnest(params) AS param FROM streams WHERE sensor_id=?" +
        ") " +
        "SELECT row_to_json(t, true) AS my_sensor FROM (" +
        "SELECT to_char(min(start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') As min_start_time, " +
        "to_char(max(end_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') As max_end_time, array_agg(distinct " +
        "param) AS parameters FROM stream_info" +
        ") As t;"
      val st = conn.prepareStatement(query)
      st.setInt(1, id.toInt)
      val rs = st.executeQuery()
      var data = ""
      while (rs.next()) {
        data += rs.getString(1)
      }
      rs.close()
      st.close()
      Json.parse(data)
    }
  }

  def getSensorStreams(id: Int): List[StreamModel] = {
    db.withConnection { conn =>
      var data = ""
      var query = "SELECT row_to_json(t,true) As my_places FROM " +
        "(SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "'Feature' As type, metadata As properties, ST_AsGeoJson(geog, 15, 0)::json As geometry, sensor_id::int, " +
        "to_char(start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS start_time, to_char(end_time AT TIME " +
        "ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS end_time, params AS parameters FROM streams"
      query += " WHERE sensor_id = " + id + " ) As t"
      val st = conn.prepareStatement(query)
      //st.setInt(1, id.toInt)
      Logger.debug("Get streams by sensor statement: " + st)
      val rs = st.executeQuery()
      var streams: ListBuffer[JsValue] = ListBuffer()
      var stream_objects: ListBuffer[StreamModel] = ListBuffer()
      while (rs.next()) {
        val current = rs.getString(1)
        var current_data = current
        streams += Json.parse(current_data)
        try {
          stream_objects += Json.parse(current_data).as[StreamModel]
        } catch {
          case e: Exception => {
            print(e.toString)
          }
        }
        data += rs.getString(1)
      }
      rs.close()
      st.close()
      var asJson = Json.parse(data)

      stream_objects.toList
    }
  }

  def updateSensorStats(sensor_id: Option[Int]): Unit = {
    db.withConnection { conn =>
      // always update the empty streams list first
      updateEmptyStats()

      // next update the streams associated with the sensor
      var query = "UPDATE streams SET start_time=n.start_time, end_time=n.end_time, params=n.params FROM ("
      query += "  SELECT stream_id, min(datapoints.start_time) AS start_time, max(datapoints.end_time) AS end_time, " +
        "array_agg(distinct keys) AS params"
      if (!sensor_id.isDefined) {
        query += "    FROM datapoints, jsonb_object_keys(data) data(keys) "
      } else {
        query += "    FROM datapoints, jsonb_object_keys(data) data(keys), streams "
        query += "    WHERE streams.gid=datapoints.stream_id AND streams.sensor_id=? "
      }
      query += "    GROUP by stream_id) n "
      query += "  WHERE n.stream_id=streams.gid;"

      val st = conn.prepareStatement(query)
      if (sensor_id.isDefined) st.setInt(1, sensor_id.get.toInt)
      Logger.debug("updateSensorStats statement: " + st)
      st.execute()
      st.close()
    }
  }

  def searchSensors(geocode: Option[String], sensor_name: Option[String]): List[SensorModel] = {
    db.withConnection { conn =>
      val parts = geocode match {
        case Some(x) => x.split(",")
        case None => Array[String]()
      }
      var i = 0
      var query = "WITH stream_info AS (" +
        "SELECT sensor_id, start_time, end_time, unnest(params) AS param FROM streams" +
        ") " +
        "SELECT row_to_json(t, true) FROM (" +
        "SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "'Feature' As type, metadata As properties, ST_AsGeoJson(geog, 15, 0)::json As geometry, " +
        "to_char(min(stream_info.start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS min_start_time, " +
        "to_char(max(stream_info.end_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS max_end_time, " +
        "array_agg(distinct stream_info.param) as parameters " +
        "FROM sensors " +
        "LEFT OUTER JOIN stream_info ON stream_info.sensor_id = sensors.gid "
      if (parts.length == 3) {
        query += "WHERE ST_DWithin(geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
        if (sensor_name.isDefined) query += " AND name = ?"
      } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
        query += "WHERE ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
        i = 0
        while (i < parts.length) {
          query += "ST_MakePoint(?, ?), "
          i += 2
        }
        query += "ST_MakePoint(?, ?)])), geog)"
        if (sensor_name.isDefined) query += " AND name = ?"
      } else if (parts.length == 0) {
        if (sensor_name.isDefined) query += " WHERE name = ?"
      }
      query += " GROUP BY id"
      query += " ORDER BY name"
      query += ") As t;"
      val st = conn.prepareStatement(query)
      i = 0
      if (parts.length == 3) {
        st.setDouble(i + 1, parts(1).toDouble)
        st.setDouble(i + 2, parts(0).toDouble)
        st.setDouble(i + 3, parts(2).toDouble * 1000)
        if (sensor_name.isDefined) st.setString(i + 4, sensor_name.getOrElse(""))
      } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
        while (i < parts.length) {
          st.setDouble(i + 1, parts(i + 1).toDouble)
          st.setDouble(i + 2, parts(i).toDouble)
          i += 2
        }
        st.setDouble(i + 1, parts(1).toDouble)
        st.setDouble(i + 2, parts(0).toDouble)
        if (sensor_name.isDefined) st.setString(i + 3, sensor_name.getOrElse(""))
      } else if (parts.length == 0 && sensor_name.isDefined) {
        st.setString(1, sensor_name.getOrElse(""))
      }
      st.setFetchSize(50)
      Logger.debug("Sensors search statement: " + st)
      val rs = st.executeQuery()
      var sensors: ListBuffer[SensorModel] = ListBuffer()
      while (rs.next()) {
        val data = rs.getString(1)
        sensors += Json.parse(data).as[SensorModel]
      }
      sensors.toList
    }
  }

  def deleteSensor(id: Int): Unit = {
    db.withConnection { conn =>
      val query = "DELETE FROM bins_year WHERE sensor_id = ?;" +
        "DELETE FROM bins_season WHERE sensor_id = ?;" +
        "DELETE FROM bins_month WHERE sensor_id = ?;" +
        "DELETE FROM bins_day WHERE sensor_id = ?;" +
        "DELETE FROM bins_hour WHERE sensor_id = ?;" +
        "DELETE FROM datapoints USING streams WHERE stream_id IN (SELECT gid FROM streams WHERE sensor_id = ?);" +
        "DELETE FROM streams WHERE gid IN (SELECT gid FROM streams WHERE sensor_id = ?);" +
        "DELETE FROM sensors WHERE gid = ?;"
      val st = conn.prepareStatement(query)
      st.setInt(1, id.toInt)
      st.setInt(2, id.toInt)
      st.setInt(3, id.toInt)
      st.setInt(4, id.toInt)
      st.setInt(5, id.toInt)
      st.setInt(6, id.toInt)
      st.setInt(7, id.toInt)
      st.setInt(8, id.toInt)
      st.executeUpdate()
      st.close()
    }
  }

  def updateEmptyStats() {
    db.withConnection { conn =>
      val query = "update streams set start_time=null, end_time=null, params=null " +
        "where not exists (select gid from datapoints where streams.gid=datapoints.stream_id)"

      val st = conn.prepareStatement(query)
      Logger.debug("updateEmptyStats statement: " + st)
      st.execute()
      st.close()
    }
  }

  def getSensorsBySource(source: String): List[SensorModel] = {
    db.withConnection { conn =>
      val query = "WITH stream_info AS (" +
        "SELECT sensor_id, start_time, end_time, unnest(params) AS param FROM streams" +
        ") " +
        "SELECT row_to_json(t, true) FROM (" +
        "SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "'Feature' As type, metadata As properties, ST_AsGeoJson(geog, 15, 0)::json As geometry, " +
        "to_char(min(stream_info.start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS min_start_time, " +
        "to_char(max(stream_info.end_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS max_end_time, " +
        "array_agg(distinct stream_info.param) as parameters " +
        "FROM sensors " +
        "LEFT OUTER JOIN stream_info ON stream_info.sensor_id = sensors.gid WHERE " +
        " sensors.metadata ->'type'->>'id' = ? " +
        " GROUP BY id ORDER BY name) as t ;"

      val st = conn.prepareStatement(query)
      st.setString(1, source)
      val rs = st.executeQuery()
      val sensors = ListBuffer.empty[SensorModel]
      while (rs.next()) {
        val data = rs.getString(1)
        sensors += Json.parse(data).as[SensorModel]
      }
      rs.close()
      st.close()
      sensors.toList
    }
  }

  def getCount(sensor_id: Option[Int]): Int = {
    var output = 0
    db.withConnection { conn =>
      var query = "SELECT COUNT(*) FROM sensors"
      sensor_id match {
        case Some(id) => {
          query += " WHERE gid = ?"
        }
        case None =>
      }
      val st = conn.prepareStatement(query)
      sensor_id match {
        case Some(id) => {
          st.setInt(1, id)
        }
        case None =>
      }
      val rs = st.executeQuery()
      while (rs.next()) {
        output = rs.getInt(1)
      }
      rs.close()
      st.close()
    }
    output
  }

}
