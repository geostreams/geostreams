package db.postgres

import java.sql.{SQLException, Statement}
import javax.inject.Inject
import play.api.Logger
import util.Parsers
import scala.collection.mutable.ListBuffer

import db.{Datapoints, Sensors}
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
class PostgresDatapoints @Inject()(db: Database, sensors: Sensors) extends Datapoints {
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

  def getDatapoint(id: Int): Option[DatapointModel] = {
    db.withConnection { conn =>
      var data = ""
      val query = "SELECT row_to_json(t,true) As my_datapoint FROM " +
        "(SELECT datapoints.gid As id, " +
        "to_char(datapoints.created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "to_char(datapoints.start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS start_time, " +
        "to_char(datapoints.end_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS end_time, " +
        "datapoints.data As properties, 'Feature' As type, " +
        "ST_AsGeoJson(1, datapoints.geog, 15, 0)::json As geometry, " +
        "stream_id::int, sensor_id::int, sensors.name as sensor_name " +
        "FROM sensors, streams, datapoints " +
        "WHERE datapoints.gid=? AND sensors.gid = streams.sensor_id AND datapoints.stream_id = streams.gid) As t;"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      Logger.debug("Datapoints get statement: " + st)
      val rs = st.executeQuery()

      var datapoint:Option[DatapointModel] = None
      while(rs.next()) {
        val data = rs.getString(1)
        datapoint = Some(Json.parse(data).as[DatapointModel])
      }
      rs.close()
      st.close()
      datapoint
    }
  }

  def searchDatapoints(since: Option[String], until: Option[String], geocode: Option[String], stream_id: Option[String], sensor_id: Option[String],
                       source: List[String], attributes: List[String], sortByStation: Boolean): Iterator[JsObject] = {
    db.withConnection { conn =>
      val parts = geocode match {
        case Some(x) => x.split(",")
        case None => Array[String]()
      }
      var query = "SELECT to_json(t) As datapoint FROM " +
        "(SELECT datapoints.gid As id, to_char(datapoints.created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, to_char(datapoints.start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS start_time, to_char(datapoints.end_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS end_time, data As properties, 'Feature' As type, ST_AsGeoJson(1, datapoints.geog, 15, 0)::json As geometry, stream_id::int, sensor_id::int, sensors.name as sensor_name FROM sensors, streams, datapoints" +
        " WHERE sensors.gid = streams.sensor_id AND datapoints.stream_id = streams.gid"

      if (since.isDefined) query += " AND datapoints.start_time >= ?"
      if (until.isDefined) query += " AND datapoints.end_time <= ?"
      if (parts.length == 3) {
        query += " AND ST_DWithin(datapoints.geog, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)"
      } else if ((parts.length >= 6) && (parts.length % 2 == 0)) {
        query += " AND ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
        var j = 0
        while (j < parts.length) {
          query += "ST_MakePoint(?, ?), "
          j += 2
        }
        query += "ST_MakePoint(?, ?)])), datapoints.geog)"
      }
      // attributes
      if (attributes.nonEmpty) {
        //if a ":" is found, assume this is a filter, otherwise it's just a presence check
        if (attributes(0).indexOf(":") > -1) {
          query += " AND (datapoints.data @> ?::jsonb"
        } else {
          query += " AND (datapoints.data ?? ?"
        }
        for (x <- 1 until attributes.size)
          if (attributes(x).indexOf(":") > -1) {
            query += " OR (datapoints.data @> ?::jsonb)"
          } else {
            query += " OR (datapoints.data ?? ?)"
          }
        query += ")"
      }
      // data source
      if (source.nonEmpty) {
        query += " AND (? = json_extract_path_text(sensors.metadata,'type','id')"
        for (x <- 1 until source.size)
          query += " OR ? = json_extract_path_text(sensors.metadata,'type','id')"
        query += ")"
      }
      //stream
      if (stream_id.isDefined) query += " AND stream_id = ?"
      //sensor
      if (sensor_id.isDefined) query += " AND sensor_id = ?"

      query += " order by "
      if (sortByStation) {
        query += "sensor_name, "
      }
      query += "start_time asc) As t;"

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
      Logger.debug("Datapoints search statement: " + st)
      val rs = st.executeQuery()

      new Iterator[JsObject] {
        var nextObject: Option[JsObject] = None

        def hasNext = {
          if (nextObject.isDefined) {
            true
          } else if (rs.isClosed) {
            false
          } else if (!rs.next) {
            rs.close()
            st.close()
            false
          } else {
            val filterAttrs = attributes.filter(attr => {
              attr.indexOf(":") == -1
            })
            if (filterAttrs.isEmpty) {
              nextObject = Some(Json.parse(rs.getString(1)).as[JsObject])
            } else {
              nextObject = Some(filterProperties(Json.parse(rs.getString(1)).as[JsObject], filterAttrs))
            }
            true
          }
        }

        def next() = {
          if (hasNext) {
            val x = nextObject.get
            nextObject = None
            x
          } else {
            null
          }
        }
      }
    }
  }

  def deleteDatapoint(id: Int): Unit = {
    db.withConnection { conn =>
      val query = "DELETE FROM datapoints WHERE gid = ?"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      st.execute()
      st.close
    }
  }

  def renameParam(oldParam: String, newParam: String, source: Option[String], region: Option[String]): Unit ={
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
      if(source.isDefined) {
        st.setString(3, source.get)
        if(region.isDefined){
          st.setString(4, region.get)
        }
      } else {
        if(region.isDefined){
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
}
