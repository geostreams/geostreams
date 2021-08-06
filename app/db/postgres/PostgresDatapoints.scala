package db.postgres

import java.sql.{ Statement, Timestamp }
import java.text.SimpleDateFormat
import akka.actor.ActorSystem
import db.{ Datapoints, Sensors }
import javax.inject.Inject
import models.DatapointModel
import models.DatapointModel._
import play.api.Logger
import play.api.db.Database
import play.api.libs.json.{ JsObject, JsValue, Json, _ }
import utils.Parsers

import scala.collection.mutable.ListBuffer

/**
 * Store datapoints in Postgres.
 */
class PostgresDatapoints @Inject() (db: Database, sensors: Sensors, actSys: ActorSystem) extends Datapoints {
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
      } else
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

  def addDatapoints(datapoints: List[DatapointModel]): Int = {
    db.withConnection { conn =>
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
      var failedToParse: Option[JsError] = None;

      var statement = "INSERT INTO datapoints(start_time, end_time, stream_id, data, geog, created) VALUES "
      datapoints.foreach(f => {
        statement += "(?, ?, ?, CAST(? AS jsonb), CAST(ST_GeomFromGeoJSON(?) AS geography), NOW()), "
      })
      // Remove trailing comma
      statement = statement.substring(0, statement.length() - 2) + ";"
      val ps = conn.prepareStatement(statement, Statement.RETURN_GENERATED_KEYS)

      var index = 0
      datapoints.foreach(dp => {
        // Set query parameters into proper positions in statement
        val start = new Timestamp(formatter.parse(dp.start_time).getTime())
        ps.setTimestamp(index + 1, new Timestamp(start.getTime))
        if (dp.end_time.isDefined) {
          val end = new Timestamp(formatter.parse(dp.end_time.get).getTime())
          ps.setTimestamp(index + 2, new Timestamp(end.getTime))
        } else
          ps.setDate(2, null)

        ps.setInt(index + 3, dp.stream_id)
        ps.setString(index + 4, Json.stringify(dp.properties))
        ps.setString(index + 5, Json.stringify(Json.toJson(dp.geometry)))
        index += 5
      })

      // Execute query and get results
      ps.executeUpdate()
      val rs = ps.getUpdateCount
      ps.close()
      rs
    }
  }

  def getDatapoint(id: Int): Option[DatapointModel] = {
    db.withConnection { conn =>
      val query = "SELECT row_to_json(t,true) As my_datapoint FROM " +
        "(SELECT datapoints.gid As id, " +
        "to_char(datapoints.created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "to_char(datapoints.start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS start_time, " +
        "to_char(datapoints.end_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS end_time, " +
        "datapoints.data As properties, 'Feature' As type, " +
        "ST_AsGeoJson(datapoints.geog, 15, 0)::json As geometry, " +
        "stream_id::int, sensor_id::int, sensors.name as sensor_name " +
        "FROM sensors, streams, datapoints " +
        "WHERE datapoints.gid=? AND sensors.gid = streams.sensor_id AND datapoints.stream_id = streams.gid) As t;"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      val rs = st.executeQuery()

      var datapoint: Option[DatapointModel] = None
      while (rs.next()) {
        val data = rs.getString(1)
        datapoint = Some(Json.parse(data).as[DatapointModel])
      }
      rs.close()
      st.close()
      datapoint
    }
  }

  def searchDatapoints(since: Option[String], until: Option[String], geocode: Option[String], stream_id: Option[String],
    sensor_id: Option[String], source: List[String], attributes: List[String], sortByStation: Boolean, season: Option[String], onlyCount: Boolean): List[JsObject] = {
    db.withConnection { conn =>
      val parts = geocode match {
        case Some(x) => x.split(",")
        case None => Array[String]()
      }
      var query = "SELECT to_json(t) AS datapoint FROM ("
      if (onlyCount) {
        query += "SELECT count(*) AS total FROM annotated_datapoints WHERE true"
      } else {
        query += "SELECT id, created, start_time, end_time, properties, type, geometry, stream_id, sensor_id, sensor_name FROM annotated_datapoints WHERE true"
      }
      season match {
        case Some(s) => s match {
          case s if s.equalsIgnoreCase("spring") => query += " AND EXTRACT(MONTH FROM start_time::date) BETWEEN 3 AND 5"
          case s if s.equalsIgnoreCase("summer") => query += " AND EXTRACT(MONTH FROM start_time::date) BETWEEN 6 AND 8"
        }
        case None => {}
      }
      if (since.isDefined) query += " AND start_time >= ?"
      if (until.isDefined) query += " AND end_time <= ?"
      if (parts.length == 3) {
        query += " AND ST_DWithin(geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
      } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
        query += " AND ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
        var j = 0
        while (j < parts.length) {
          query += "ST_MakePoint(?, ?), "
          j += 2
        }
        query += "ST_MakePoint(?, ?)])), geog)"
      }
      // attributes
      if (attributes.nonEmpty) {
        //if a ":" is found, assume this is a filter, otherwise it's just a presence check
        if (attributes(0).indexOf(":") > -1) {
          query += " AND (properties @> ?::jsonb"
        } else {
          query += " AND (properties ?? ?"
        }
        for (x <- 1 until attributes.size)
          if (attributes(x).indexOf(":") > -1) {
            query += " OR (properties @> ?::jsonb)"
          } else {
            query += " OR (properties ?? ?)"
          }
        query += ")"
      }
      // data source
      if (source.nonEmpty) {
        query += " AND (? = json_extract_path_text(metadata,'type','id')"
        for (x <- 1 until source.size)
          query += " OR ? = json_extract_path_text(metadata,'type','id')"
        query += ")"
      }
      //stream
      if (stream_id.isDefined) query += " AND stream_id = ?"
      //sensor
      if (sensor_id.isDefined) query += " AND sensor_id = ?"
      if (sortByStation) {
        query += " order by sensor_name asc"
      }
      query += ") As t;"
      // Populate values ------
      val st = conn.prepareStatement(query)
      var i = 0
      if (since.isDefined) {
        i = i + 1
        st.setTimestamp(i, new Timestamp(Parsers.parseDate(since.get).get.getMillis))
      }
      if (until.isDefined) {
        i = i + 1
        st.setTimestamp(i, new Timestamp(Parsers.parseDate(until.get).get.getMillis))
      }
      if (parts.length == 3) {
        st.setDouble(i + 1, parts(1).toDouble)
        st.setDouble(i + 2, parts(0).toDouble)
        st.setDouble(i + 3, parts(2).toDouble * 1000)
        i += 3
      } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
        var j = 0
        while (j < parts.length) {
          st.setDouble(i + 1, parts(j + 1).toDouble)
          st.setDouble(i + 2, parts(j).toDouble)
          i += 2
          j += 2
        }
        st.setDouble(i + 1, parts(1).toDouble)
        st.setDouble(i + 2, parts(0).toDouble)
        i += 2
      }
      // attributes
      if (attributes.nonEmpty) {
        for (x <- 0 until attributes.size) {
          i = i + 1
          st.setString(i, attributes(x))
        }
      }
      // sources
      if (source.nonEmpty) {
        for (x <- 0 until source.size) {
          i = i + 1
          st.setString(i, source(x))
        }
      }
      // stream
      if (stream_id.isDefined) {
        i = i + 1
        st.setInt(i, stream_id.get.toInt)
      }
      // sensor
      if (sensor_id.isDefined) {
        i = i + 1
        st.setInt(i, sensor_id.get.toInt)
      }
      st.setFetchSize(50)
      Logger.debug("Geostream search: " + st)
      val rs = st.executeQuery()
      val array = ListBuffer.empty[JsObject]
      while (rs.next()) {
        val datapoint = Json.parse(rs.getString(1)).as[JsObject]
        array += datapoint
      }
      array.toList
    }
  }

  def deleteDatapoint(id: Int): Unit = {
    db.withConnection { conn =>
      val query = "DELETE FROM datapoints WHERE gid = ?"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      st.execute()
      st.close
      // TODO: Update necessary bins
    }
  }

  def renameParam(oldParam: String, newParam: String, source: Option[String], region: Option[String]): Unit = {
    db.withConnection { conn =>
      val query = "UPDATE datapoints SET data = replace(data::text, ?, ?)::json"
      val queryConditionStart = " WHERE gid in (SELECT datapoints.gid FROM datapoints, streams, sensors WHERE sensors.gid " +
        "= streams.sensor_id AND datapoints.stream_id = streams.gid"
      val queryCondition = (source, region) match {
        case (Some(s), Some(r)) => queryConditionStart + " AND json_extract_path_text(sensors.metadata,'type','id') " +
          "= ? AND sensors.metadata ->> 'region'::text LIKE ?)"
        case (Some(s), None) => queryConditionStart + " AND json_extract_path_text(sensors.metadata,'type','id') = ? )"
        case (None, Some(r)) => queryConditionStart + " AND sensors.metadata ->> 'region'::text LIKE ? )"
        case (None, None) => "" //no need to link sensors and streams
      }

      val st = conn.prepareStatement(query + queryCondition)
      st.setString(1, oldParam)
      st.setString(2, newParam)
      if (source.isDefined) {
        st.setString(3, source.get)
        if (region.isDefined) {
          st.setString(4, region.get)
        }
      } else {
        if (region.isDefined) {
          st.setString(3, region.get)
        }
      }

      st.execute()
      st.close

      sensors.updateSensorStats(None)
    }
  }

  private def filterProperties(obj: JsObject, attributes: List[String]) = {
    var props = JsObject(Seq.empty)
    (obj \ "properties").asOpt[JsObject] match {
      case Some(x) => {
        for (f <- x.fieldSet) {
          if (("source" == f._1) || attributes.contains(f._1)) {
            props = props + f
          }
        }
        (obj - ("properties") + ("properties", props))
      }
      case None => obj
    }
  }

  def getCount(sensor_id: Option[Int]): Int = {
    var output = 0
    db.withConnection { conn =>
      var query = "SELECT COUNT(*) FROM datapoints"
      sensor_id match {
        case Some(id) => {
          query += " WHERE stream_id IN (SELECT gid FROM streams WHERE sensor_id = ?)"
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
