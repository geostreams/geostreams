package db.postgres

import db.{ RegionTrends, Sensors }
import javax.inject.Inject
import models.RegionModel
import play.api.Logger
import play.api.db.Database
import play.api.libs.json.{ JsObject, JsValue, Json }

import scala.collection.mutable.ListBuffer

class PostgresRegionTrends @Inject() (db: Database, sensors: Sensors) extends RegionTrends {

  def trendsByRegion(attribute: String, geocode: String, latFirst: Boolean): List[JsValue] = {
    db.withConnection { conn =>
      var query = "SELECT to_json(t) As datapoint FROM (SELECT (datapoints.data ->> ?)::text AS data, " +
        "to_char(datapoints.start_time AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS time FROM sensors, streams, " +
        "datapoints WHERE sensors.gid = streams.sensor_id AND datapoints.stream_id = streams.gid AND datapoints.data ?? ? "
      query += " AND ST_Covers(ST_MakePolygon(ST_MakeLine(ARRAY["
      val parts = geocode.split(",")
      var j = 0
      while (j < parts.length - 2) {
        query += "ST_MakePoint(?, ?), "
        j += 2
      }
      query += "ST_MakePoint(?, ?)])), datapoints.geog)"

      query += ") AS t; "
      val st = conn.prepareStatement(query)

      st.setString(1, attribute)
      st.setString(2, attribute)
      j = 0
      while (j < parts.length) {
        if (latFirst) {
          st.setDouble(j + 3, parts(j + 1).toDouble)
          st.setDouble(j + 4, parts(j).toDouble)
        } else {
          st.setDouble(j + 3, parts(j).toDouble)
          st.setDouble(j + 4, parts(j + 1).toDouble)
        }
        j += 2
      }

      // for test
      // println(st.toString)

      val rs = st.executeQuery()
      var filtereddata: ListBuffer[JsValue] = ListBuffer()
      while (rs.next()) {
        val data = rs.getString(1)
        filtereddata += Json.parse(data)
      }
      rs.close()
      st.close()
      filtereddata.toList
    }
  }

  def getTrendsByRegion(attribute: String, season: String): List[JsObject] = {
    db.withConnection { conn =>
      var query = "SELECT to_json(t) As trends FROM (SELECT totalaverage, tenyearsaverage, lastaverage, region_id from " +
        "region_trends where parameter = ? AND season = ?) As t"
      val st = conn.prepareStatement(query)

      st.setString(1, attribute)
      st.setString(2, season)
      // for test
      // println(st.toString)

      val rs = st.executeQuery()
      val array = ListBuffer.empty[JsObject]
      while (rs.next()) {

        array += Json.parse(rs.getString(1)).as[JsObject]
      }
      rs.close()
      st.close()
      array.toList
    }
  }

  def saveRegion(region: RegionModel) = {
    try {
      db.withConnection { conn =>

        var query = "INSERT INTO regions(id, title) " +
          "VALUES(?, ?)"

        val st = conn.prepareStatement(query)

        st.setString(1, region.properties.\("id").as[String])
        st.setString(2, region.properties.\("title").as[String])

        st.executeUpdate()
        val rs = st.getGeneratedKeys
        var id = 0
        while (rs.next()) {
          id = rs.getInt(1)
        }
        rs.close()
        st.close()
      }
    } catch {
      // do nothing if it is PSQLException, the error will show in the log. otherwise print
      case e: org.postgresql.util.PSQLException =>
      case e: Exception => e.printStackTrace()
    }
  }

  def saveRegionTrends(region: RegionModel, season: String, parameter: String, average: List[Double]) = {
    db.withConnection { conn =>

      var query = "INSERT INTO region_trends(region_id, season, parameter, lastaverage, tenyearsaverage, totalaverage) " +
        "VALUES(?, ?, ?, ?, ?, ?) ON CONFLICT (region_id, season, parameter) DO UPDATE " +
        "SET lastaverage = EXCLUDED.lastaverage, tenyearsaverage = EXCLUDED.tenyearsaverage, totalaverage = EXCLUDED.totalaverage"

      val st = conn.prepareStatement(query)

      st.setString(1, region.properties.\("id").as[String])
      st.setString(2, season)
      st.setString(3, parameter)
      st.setDouble(4, average(0))
      st.setDouble(5, average(1))
      st.setDouble(6, average(2))
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
