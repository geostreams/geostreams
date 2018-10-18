package db.postgres

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.{ Collections, Date }

import akka.actor.ActorSystem
import db.{ Cache, Sensors }
import javax.inject.Inject

import play.api.db.Database

import scala.collection.mutable.ListBuffer
import utils.DatapointsHelper
import models.{ DatapointModel, SensorModel }

/**
 * Store aggregate statistics in cache bins to speed up retrieval.
 */
class PostgresCache @Inject() (db: Database, sensors: Sensors, actSys: ActorSystem) extends Cache {

  /* Perform actual query to aggregate datapoint parameter values across specified time range and return resulting bins.
   *  since, until -- SQL query timestamps, e.g. '2017-12', '2013-10-38T12:57:59.923'
   */
  private def aggregateStatsByYear(sensor_id: Int, since: Option[String], until: Option[String], parameter: String): List[(Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var bulk_stats = List[(Int, Int, Double, Double, Timestamp, Timestamp)]()
    db.withConnection { conn =>
      var query =
        "SELECT extract(year from datapoints.start_time) as yyyy, " +
          "             count(datapoints.data ->> ?) as count, " +
          "             sum(cast_to_double( datapoints.data ->> ?)) as sum, " +
          "             avg(cast_to_double( datapoints.data ->> ?)) as avg, " +
          "             min(datapoints.start_time) as start_time, max(datapoints.end_time) as end_time " +
          "      from datapoints, streams " +
          "      WHERE datapoints.stream_id = streams.gid and streams.sensor_id = ? "

      query += since.fold("")(n => " and datapoints.start_time >= ?")
      query += until.fold("")(n => " and datapoints.start_time <= ?")
      query += " group by yyyy;"
      val st = conn.prepareStatement(query)

      st.setString(1, parameter)
      st.setString(2, parameter)
      st.setString(3, parameter)
      st.setInt(4, sensor_id)
      var i = 5
      since match {
        case Some(n) => {
          st.setString(i, n)
          i += 1
        }
        case None => {}
      }
      until match {
        case Some(n) => {
          st.setString(i, n)
          i += 1
        }
        case None => {}
      }
      val stats = st.executeQuery()

      while (stats.next()) {
        if (stats.getInt(2) > 0)
          bulk_stats = bulk_stats :+ (
            stats.getInt(1), // year
            stats.getInt(2), // count
            stats.getDouble(3), // sum
            stats.getDouble(4), // avg
            stats.getTimestamp(5), // start_time
            stats.getTimestamp(6) // end_time
          )
      }

      stats.close()
      st.close()
    }
    bulk_stats
  }

  private def aggregateStatsBySeason(sensor_id: Int, since: Option[String], until: Option[String], parameter: String): List[(Int, String, Int, Double, Double, Timestamp, Timestamp)] = {

    sensors.getSensor(sensor_id) match {
      case Some(sensor) => {
        val (start_year, end_year, _, _, _, _, _, _) = DatapointsHelper.parseTimeRange(sensor.min_start_time, sensor.max_end_time)
        var bulk_stats = List[(Int, String, Int, Double, Double, Timestamp, Timestamp)]()
        for (current_year <- start_year to end_year) {
          val winter = aggregateStatsBySeasonHelper(
            sensor_id,
            Some((current_year - 1).toString + "-12-21"),
            Some(current_year.toString + "-03-20"),
            parameter, "winter"
          )
          bulk_stats = bulk_stats ++ winter
          // SPRING = 03/21 - 06/20
          val spring = aggregateStatsBySeasonHelper(
            sensor_id,
            Some(current_year.toString + "-03-21"),
            Some(current_year.toString + "-06-20"),
            parameter, "spring"
          )
          bulk_stats = bulk_stats ++ spring
          // SUMMER = 06/21 - 09/20
          val summer = aggregateStatsBySeasonHelper(
            sensor_id,
            Some(current_year.toString + "-06-21"),
            Some(current_year.toString + "-09-20"),
            parameter, "summer"
          )
          bulk_stats = bulk_stats ++ summer
          // FALL = 09/21 - 12/20
          val fall = aggregateStatsBySeasonHelper(
            sensor_id,
            Some(current_year.toString + "-09-21"),
            Some(current_year.toString + "-12-20"),
            parameter, "fall"
          )
          bulk_stats = bulk_stats ++ fall
        }
        bulk_stats
      }
      case None => { List.empty }
    }

  }
  private def aggregateStatsBySeasonHelper(sensor_id: Int, since: Option[String], until: Option[String], parameter: String, season: String): List[(Int, String, Int, Double, Double, Timestamp, Timestamp)] = {

    var temp_stats = List[(Int, Int, Double, Double, Timestamp, Timestamp)]()
    var bulk_stats = List[(Int, String, Int, Double, Double, Timestamp, Timestamp)]()
    db.withConnection { conn =>
      var query =
        "SELECT extract(year from datapoints.start_time) as yyyy, " +
          "             count(datapoints.data ->> ?) as count, " +
          "             sum(cast_to_double( datapoints.data ->> ?)) as sum, " +
          "             avg(cast_to_double( datapoints.data ->> ?)) as avg, " +
          "             min(datapoints.start_time) as start_time, max(datapoints.end_time) as end_time " +
          "      from datapoints, streams " +
          "      WHERE datapoints.stream_id = streams.gid and streams.sensor_id = ?"
      query += since.fold("")(n => " and datapoints.start_time >= ?::date")
      query += until.fold("")(n => " and datapoints.start_time <= ?::date")
      query += " group by 1;"
      val st = conn.prepareStatement(query)

      st.setString(1, parameter)
      st.setString(2, parameter)
      st.setString(3, parameter)
      st.setInt(4, sensor_id)
      var i = 5
      since match {
        case Some(n) => {
          st.setString(i, n)
          i += 1
        }
        case None => {}
      }
      until match {
        case Some(n) => {
          st.setString(i, n)
          i += 1
        }
        case None => {}
      }
      val stats = st.executeQuery()

      while (stats.next()) {
        if (stats.getInt(3) > 0)
          temp_stats = temp_stats :+ (
            stats.getInt(1), // year
            stats.getInt(2), // count
            stats.getDouble(3), // sum
            stats.getDouble(4), // avg
            stats.getTimestamp(5), // start_time
            stats.getTimestamp(6) // end_time
          )
      }
      stats.close()
      st.close()
    }
    if (temp_stats.length > 0) {
      var season_count = 0
      var season_sum = 0.0
      var season_year = 0
      var season_start_time: Timestamp = temp_stats.head._5
      var season_end_time: Timestamp = temp_stats.head._6
      temp_stats.foreach(s => {
        val (year, count, sum, avg, start_time, end_time) = s
        season_count += count
        season_sum += sum
        season_year = if (season_year > year) season_year else year

        season_start_time = if (season_start_time.getTime() > start_time.getTime()) start_time else season_start_time
        season_end_time = if (season_end_time.getTime() < end_time.getTime()) end_time else season_end_time

      })
      val season_avg = if (season_count > 0) season_sum / season_count else 0
      bulk_stats = bulk_stats :+ (season_year, season, season_count, season_sum, season_avg, season_start_time, season_end_time)
    }

    bulk_stats
  }

  private def aggregateStatsByMonth(sensor_id: Int, since: Option[String], until: Option[String], parameter: String): List[(Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var bulk_stats = List[(Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
    db.withConnection { conn =>
      var query =
        "SELECT extract(year from datapoints.start_time) as yyyy, " +
          "      extract(month from datapoints.start_time) as mm, " +
          "             count(datapoints.data ->> ?) as count, " +
          "             sum(cast_to_double( datapoints.data ->> ?)) as sum, " +
          "             avg(cast_to_double( datapoints.data ->> ?)) as avg, " +
          "             min(datapoints.start_time) as start_time, max(datapoints.end_time) as end_time " +
          "      from datapoints, streams " +
          "      WHERE datapoints.stream_id = streams.gid and streams.sensor_id = ?"
      query += since.fold("")(n => " and datapoints.start_time >= ?")
      query += until.fold("")(n => " and datapoints.start_time <= ?")
      query += " group by yyyy, mm;"
      val st = conn.prepareStatement(query)

      st.setString(1, parameter)
      st.setString(2, parameter)
      st.setString(3, parameter)
      st.setInt(4, sensor_id)
      var i = 5
      since match {
        case Some(n) => {
          st.setString(i, n)
          i += 1
        }
        case None => {}
      }
      until match {
        case Some(n) => {
          st.setString(i, n)
          i += 1
        }
        case None => {}
      }
      val stats = st.executeQuery()

      while (stats.next()) {
        if (stats.getInt(3) > 0)
          bulk_stats = bulk_stats :+ (
            stats.getInt(1), // year
            stats.getInt(2), // month
            stats.getInt(3), // count
            stats.getDouble(4), // sum
            stats.getDouble(5), // avg
            stats.getTimestamp(6), // start_time
            stats.getTimestamp(7) // end_time
          )
      }
      stats.close()
      st.close()
    }
    bulk_stats
  }

  private def aggregateStatsByDay(sensor_id: Int, since: Option[String], until: Option[String], parameter: String): List[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var bulk_stats = List[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
    db.withConnection { conn =>
      var query =
        "SELECT extract(year from datapoints.start_time) as yyyy, " +
          "      extract(month from datapoints.start_time) as mm, " +
          "      extract(day from datapoints.start_time) as dd, " +
          "             count(datapoints.data ->> ?) as count, " +
          "             sum(cast_to_double( datapoints.data ->> ?)) as sum, " +
          "             avg(cast_to_double( datapoints.data ->> ?)) as avg, " +
          "             min(datapoints.start_time) as start_time, max(datapoints.end_time) as end_time " +
          "      from datapoints, streams " +
          "      WHERE datapoints.stream_id = streams.gid and streams.sensor_id = ?"
      query += since.fold("")(n => " and datapoints.start_time >= ?")
      query += until.fold("")(n => " and datapoints.start_time <= ?")
      query += " group by yyyy, mm, dd;"
      val st = conn.prepareStatement(query)

      st.setString(1, parameter)
      st.setString(2, parameter)
      st.setString(3, parameter)
      st.setInt(4, sensor_id)
      var i = 5
      since match {
        case Some(n) => {
          st.setString(i, n)
          i += 1
        }
        case None => {}
      }
      until match {
        case Some(n) => {
          st.setString(i, n)
          i += 1
        }
        case None => {}
      }
      val stats = st.executeQuery()

      while (stats.next()) {
        if (stats.getInt(4) > 0)
          bulk_stats = bulk_stats :+ (
            stats.getInt(1), // year
            stats.getInt(2), // month
            stats.getInt(3), // day
            stats.getInt(4), // count
            stats.getDouble(5), // sum
            stats.getDouble(6), // avg
            stats.getTimestamp(7), // start_time
            stats.getTimestamp(8) // end_time
          )
      }
      stats.close()
      st.close()
    }
    bulk_stats
  }

  private def aggregateStatsByHour(sensor_id: Int, since: Option[String], until: Option[String], parameter: String): List[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var bulk_stats = List[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
    db.withConnection { conn =>
      var query =
        "SELECT extract(year from datapoints.start_time) as yyyy, " +
          "      extract(month from datapoints.start_time) as mm, " +
          "      extract(day from datapoints.start_time) as dd, " +
          "      extract(hour from datapoints.start_time) as hh, " +
          "             count(datapoints.data ->> ?) as count, " +
          "             sum(cast_to_double( datapoints.data ->> ?)) as sum, " +
          "             avg(cast_to_double( datapoints.data ->> ?)) as avg, " +
          "             min(datapoints.start_time) as start_time, max(datapoints.end_time) as end_time " +
          "      from datapoints, streams " +
          "      WHERE datapoints.stream_id = streams.gid and streams.sensor_id = ?"
      query += since.fold("")(n => " and datapoints.start_time >= ?")
      query += until.fold("")(n => " and datapoints.start_time <= ?")
      query += " group by yyyy, mm, dd, hh;"
      val st = conn.prepareStatement(query)

      st.setString(1, parameter)
      st.setString(2, parameter)
      st.setString(3, parameter)
      st.setInt(4, sensor_id)
      var i = 5
      since match {
        case Some(n) => {
          st.setString(i, n)
          i += 1
        }
        case None => {}
      }
      until match {
        case Some(n) => {
          st.setString(i, n)
          i += 1
        }
        case None => {}
      }
      val stats = st.executeQuery()

      while (stats.next()) {
        if (stats.getInt(5) > 0)
          bulk_stats = bulk_stats :+ (
            stats.getInt(1), // year
            stats.getInt(2), // month
            stats.getInt(3), // day
            stats.getInt(4), // hour
            stats.getInt(5), // count
            stats.getDouble(6), // sum
            stats.getDouble(7), // avg
            stats.getTimestamp(8), // start_time
            stats.getTimestamp(9) // end_time
          )
      }
      stats.close()
      st.close()
    }
    bulk_stats
  }

  /* Calculate bin statistics and insert them into database, updating if records already exist.
   *  since, until -- SQL query timestamps, e.g. '2017-12', '2013-10-38T12:57:59.923'
   */
  def calculateBinsByYear(sensor_id: Int, since: Option[String], until: Option[String], parameter: String): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val stats_values = aggregateStatsByYear(sensor_id, since, until, parameter)
      var included_count = 0

      if (stats_values.length > 0) {
        var query = "insert into bins_year (sensor_id, yyyy, parameter, datapoint_count, sum, average, start_time, end_time, updated) values "
        var first = true
        stats_values.foreach(s => {
          if (!first) {
            query += ", "
          } else {
            first = false
          }
          query += "(?, ?, ?, ?, ?, ?, ?, ?, ?)"
          included_count += 1
        })

        // Only execute query if we had any valid bins
        if (included_count > 0) {
          // excluded - https://stackoverflow.com/questions/34514457/bulk-insert-update-if-on-conflict-bulk-upsert-on-postgres
          query += " on conflict (sensor_id, yyyy, parameter) do update " +
            "set datapoint_count = excluded.datapoint_count, sum = excluded.sum, average = excluded.average, " +
            "start_time = excluded.start_time, end_time = excluded.end_time, updated = excluded.updated " +
            "where bins_year.sensor_id = excluded.sensor_id and bins_year.yyyy = excluded.yyyy " +
            "and bins_year.parameter = excluded.parameter;"

          val st = conn.prepareStatement(query)
          var i = 0
          stats_values.foreach(s => {
            val (current_year, count, sum, avg, start_time, end_time) = s
            st.setInt(i + 1, sensor_id)
            st.setInt(i + 2, current_year)
            st.setString(i + 3, parameter)
            st.setInt(i + 4, count)
            st.setDouble(i + 5, sum)
            st.setDouble(i + 6, avg)
            st.setTimestamp(i + 7, start_time)
            st.setTimestamp(i + 8, end_time)
            st.setTimestamp(i + 9, curr_time)
            i += 9
          })
          st.execute()
          st.close()
        }
      }
    }
  }

  def calculateBinsBySeason(sensor_id: Int, since: Option[String], until: Option[String], parameter: String): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val stats_values = aggregateStatsBySeason(sensor_id, since, until, parameter)
      var included_count = 0

      if (stats_values.length > 0) {
        var query = "insert into bins_season (sensor_id, yyyy, season, parameter, datapoint_count, sum, average, start_time, end_time, updated) values "
        var first = true
        stats_values.foreach(s => {
          if (!first) {
            query += ", "
          } else {
            first = false
          }
          query += "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          included_count += 1
        })

        // Only execute query if we had any valid bins
        if (included_count > 0) {
          // excluded - https://stackoverflow.com/questions/34514457/bulk-insert-update-if-on-conflict-bulk-upsert-on-postgres
          query += " on conflict (sensor_id, yyyy, season, parameter) do update " +
            "set datapoint_count = excluded.datapoint_count, sum = excluded.sum, average = excluded.average, " +
            "start_time = excluded.start_time, end_time = excluded.end_time, updated = excluded.updated " +
            "where bins_season.sensor_id = excluded.sensor_id and bins_season.yyyy = excluded.yyyy and " +
            "bins_season.season = excluded.season and bins_season.parameter = excluded.parameter;"

          val st = conn.prepareStatement(query)
          var i = 0
          stats_values.foreach(s => {
            val (current_year, current_season, count, sum, avg, start_time, end_time) = s
            st.setInt(i + 1, sensor_id)
            st.setInt(i + 2, current_year)
            st.setString(i + 3, current_season)
            st.setString(i + 4, parameter)
            st.setInt(i + 5, count)
            st.setDouble(i + 6, sum)
            st.setDouble(i + 7, avg)
            st.setTimestamp(i + 8, start_time)
            st.setTimestamp(i + 9, end_time)
            st.setTimestamp(i + 10, curr_time)
            i += 10
          })
          st.execute()
          st.close()
        }
      }
    }
  }

  def calculateBinsByMonth(sensor_id: Int, since: Option[String], until: Option[String], parameter: String): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val stats_values = aggregateStatsByMonth(sensor_id, since, until, parameter)
      var included_count = 0

      if (stats_values.length > 0) {
        var query = "insert into bins_month (sensor_id, yyyy, mm, parameter, datapoint_count, sum, average, start_time, end_time, updated) values "
        var first = true
        stats_values.foreach(s => {
          if (!first) {
            query += ", "
          } else {
            first = false
          }
          query += "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          included_count += 1
        })

        // Only execute query if we had any valid bins
        if (included_count > 0) {
          // excluded - https://stackoverflow.com/questions/34514457/bulk-insert-update-if-on-conflict-bulk-upsert-on-postgres
          query += " on conflict (sensor_id, yyyy, mm, parameter) do update " +
            "set datapoint_count = excluded.datapoint_count, sum = excluded.sum, average = excluded.average, " +
            "start_time = excluded.start_time, end_time = excluded.end_time, updated = excluded.updated " +
            "where bins_month.sensor_id = excluded.sensor_id and bins_month.yyyy = excluded.yyyy and " +
            "bins_month.mm = excluded.mm and bins_month.parameter = excluded.parameter;"

          val st = conn.prepareStatement(query)
          var i = 0
          stats_values.foreach(s => {
            val (current_year, current_month, count, sum, avg, start_time, end_time) = s
            st.setInt(i + 1, sensor_id)
            st.setInt(i + 2, current_year)
            st.setInt(i + 3, current_month)
            st.setString(i + 4, parameter)
            st.setInt(i + 5, count)
            st.setDouble(i + 6, sum)
            st.setDouble(i + 7, avg)
            st.setTimestamp(i + 8, start_time)
            st.setTimestamp(i + 9, end_time)
            st.setTimestamp(i + 10, curr_time)
            i += 10
          })
          st.execute()
          st.close()
        }
      }
    }
  }

  def calculateBinsByDay(sensor_id: Int, since: Option[String], until: Option[String], parameter: String): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val stats_values = aggregateStatsByDay(sensor_id, since, until, parameter)

      // Limit maximum length of a query
      val max_batch_size = 1000
      // A list of tuples with (query string, list of values to populate)
      var query_list = ListBuffer[(String, List[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)])]()

      if (stats_values.length > 0) {
        var query = "insert into bins_day (sensor_id, yyyy, mm, dd, parameter, datapoint_count, sum, average, start_time, end_time, updated) values "
        var first = true
        var curr_batch = ListBuffer[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
        var curr_batch_size = 0

        stats_values.foreach(s => {
          if (!first) {
            query += ", "
          } else {
            first = false
          }
          query += "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          curr_batch += s
          curr_batch_size += 1
          if (curr_batch_size > max_batch_size) {
            query_list += ((query, curr_batch.toList))
            query = "insert into bins_day (sensor_id, yyyy, mm, dd, parameter, datapoint_count, sum, average, start_time, end_time, updated) values "
            first = true
            curr_batch = ListBuffer[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
            curr_batch_size = 0
          }
        })
        if (curr_batch_size > 0)
          query_list += ((query, curr_batch.toList))

        // Only execute query if we had any valid bins
        query_list.foreach({
          case (query, value_list) => {
            // excluded - https://stackoverflow.com/questions/34514457/bulk-insert-update-if-on-conflict-bulk-upsert-on-postgres
            val complete_query = query + " on conflict (sensor_id, yyyy, mm, dd, parameter) do update " +
              "set datapoint_count = excluded.datapoint_count, sum = excluded.sum, average = excluded.average, " +
              "start_time = excluded.start_time, end_time = excluded.end_time, updated = excluded.updated " +
              "where bins_day.sensor_id = excluded.sensor_id and bins_day.yyyy = excluded.yyyy and " +
              "bins_day.mm = excluded.mm and bins_day.dd = excluded.dd and " +
              "bins_day.parameter = excluded.parameter;"

            val st = conn.prepareStatement(complete_query)
            var i = 0
            value_list.foreach(s => {
              val (current_year, current_month, current_day, count, sum, avg, start_time, end_time) = s
              st.setInt(i + 1, sensor_id)
              st.setInt(i + 2, current_year)
              st.setInt(i + 3, current_month)
              st.setInt(i + 4, current_day)
              st.setString(i + 5, parameter)
              st.setInt(i + 6, count)
              st.setDouble(i + 7, sum)
              st.setDouble(i + 8, avg)
              st.setTimestamp(i + 9, start_time)
              st.setTimestamp(i + 10, end_time)
              st.setTimestamp(i + 11, curr_time)
              i += 11

            })
            st.execute()
            st.close()
          }
        })
      }
    }
  }

  def calculateBinsByHour(sensor_id: Int, since: Option[String], until: Option[String], parameter: String): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val stats_values = aggregateStatsByHour(sensor_id, since, until, parameter)

      // Limit maximum length of a query
      val max_batch_size = 1000
      // A list of tuples with (query string, list of values to populate)
      var query_list = ListBuffer[(String, List[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)])]()

      if (stats_values.length > 0) {
        var query = "insert into bins_hour (sensor_id, yyyy, mm, dd, hh, parameter, datapoint_count, sum, average, start_time, end_time, updated) values "
        var first = true
        var curr_batch = ListBuffer[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
        var curr_batch_size = 0

        stats_values.foreach(s => {
          if (!first) {
            query += ", "
          } else {
            first = false
          }
          query += "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          curr_batch += s
          curr_batch_size += 1
          if (curr_batch_size > max_batch_size) {
            query_list += ((query, curr_batch.toList))
            query = "insert into bins_hour (sensor_id, yyyy, mm, dd, hh, parameter, datapoint_count, sum, average, start_time, end_time, updated) values "
            first = true
            curr_batch = ListBuffer[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
            curr_batch_size = 0
          }
        })
        if (curr_batch_size > 0)
          query_list += ((query, curr_batch.toList))

        // Only execute query if we had any valid bins
        query_list.foreach({
          case (query, value_list) => {
            // excluded - https://stackoverflow.com/questions/34514457/bulk-insert-update-if-on-conflict-bulk-upsert-on-postgres
            val complete_query = query + " on conflict (sensor_id, yyyy, mm, dd, hh, parameter) do update " +
              "set datapoint_count = excluded.datapoint_count, sum = excluded.sum, average = excluded.average, " +
              "start_time = excluded.start_time, end_time = excluded.end_time, updated = excluded.updated " +
              "where bins_hour.sensor_id = excluded.sensor_id and bins_hour.yyyy = excluded.yyyy and " +
              "bins_hour.mm = excluded.mm and bins_hour.dd = excluded.dd and bins_hour.hh = excluded.hh and " +
              "bins_hour.parameter = excluded.parameter;"

            val st = conn.prepareStatement(complete_query)
            var i = 0
            value_list.foreach(s => {
              val (current_year, current_month, current_day, current_hour, count, sum, avg, start_time, end_time) = s
              st.setInt(i + 1, sensor_id)
              st.setInt(i + 2, current_year)
              st.setInt(i + 3, current_month)
              st.setInt(i + 4, current_day)
              st.setInt(i + 5, current_hour)
              st.setString(i + 6, parameter)
              st.setInt(i + 7, count)
              st.setDouble(i + 8, sum)
              st.setDouble(i + 9, avg)
              st.setTimestamp(i + 10, start_time)
              st.setTimestamp(i + 11, end_time)
              st.setTimestamp(i + 12, curr_time)
              i += 12

            })
            st.execute()
            st.close()
          }
        })
      }
    }
  }

  /* Fetch existing bin stats (or create new bin) and add single datapoint to count/sum/average.
   *  since, until -- SQL query timestamps, e.g. '2017-12', '2013-10-38T12:57:59.923'
   */
  def insertIntoBinYear(sensor: SensorModel, dp: DatapointModel, yyyy: Int, parameter: String, value: Double): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val bin_time = Some(yyyy.toString)
      val existing_stats = getCachedBinStatsByYear(sensor, bin_time, bin_time, parameter, false)

      if (existing_stats.length > 0) {
        // Need to append to existing bin(s)
        existing_stats.foreach(s => {
          val (yyyy, count, sum, avg, start_time, end_time) = s
          val new_count = count + 1
          val new_sum = sum + value
          val new_avg = new_sum / new_count

          val query = "update bins_year set datapoint_count = ?, sum = ?, average = ?, updated = ? " +
            "where sensor_id = ? and yyyy = ? and parameter = ?;"
          val st = conn.prepareStatement(query)
          st.setInt(1, new_count)
          st.setDouble(2, new_sum)
          st.setDouble(3, new_avg)
          st.setTimestamp(4, curr_time)
          st.setInt(5, sensor.id)
          st.setInt(6, yyyy)
          st.setString(7, parameter)
          st.execute()
          st.close()
        })

      } else {
        // Need to create a new bin
        val query = "insert into bins_year (sensor_id, yyyy, parameter, datapoint_count, sum, average, start_time, end_time, updated) " +
          "values (?, ?, ?, ?, ?, ?, ?, ?, ?);"
        val st = conn.prepareStatement(query)
        st.setInt(1, sensor.id)
        st.setInt(2, yyyy)
        st.setString(3, parameter)
        st.setInt(4, 1)
        st.setDouble(5, value)
        st.setDouble(6, value)
        val format = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss.SSSz")
        st.setTimestamp(7, new Timestamp(format.parse(dp.start_time).getTime))
        st.setTimestamp(8, dp.end_time match {
          case Some(et) => new Timestamp(format.parse(et).getTime)
          case None => new Timestamp(format.parse(dp.start_time).getTime)
        })
        st.setTimestamp(9, curr_time)
        st.execute()
        st.close()
      }
    }
  }

  def insertIntoBinSeason(sensor: SensorModel, dp: DatapointModel, yyyy: Int, season: String, parameter: String, value: Double): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val bin_time = Some(yyyy.toString + "-" + season)
      val existing_stats = getCachedBinStatsBySeason(sensor, Some(season), bin_time, bin_time, parameter, false)

      if (existing_stats.length > 0) {
        existing_stats.foreach(s => {
          val (yyyy, season, count, sum, avg, start_time, end_time) = s
          val new_count = count + 1
          val new_sum = sum + value
          val new_avg = new_sum / new_count

          val query = "UPDATE bins_season set datapoint_count = ?, sum = ?, average = ?, updated = ?" +
            "WHERE sensor_id = ? and yyyy =? and season = ? and parameter = ?;"

          val st = conn.prepareStatement(query)
          st.setInt(1, new_count)
          st.setDouble(2, new_sum)
          st.setDouble(3, new_avg)
          st.setTimestamp(4, curr_time)
          st.setInt(5, sensor.id)
          st.setInt(6, yyyy)
          st.setString(7, season)
          st.setString(8, parameter)
          st.execute()
          st.close()
        })
      } else {
        val query = "insert into bins_season (sensor_id, yyyy, season, parameter, datapoint_count, sum, average, start_time, end_time, updated) " +
          "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
        val st = conn.prepareStatement(query)
        st.setInt(1, sensor.id)
        st.setInt(2, yyyy)
        st.setString(3, season)
        st.setString(4, parameter)
        st.setInt(5, 1)
        st.setDouble(6, value)
        st.setDouble(7, value)
        val format = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss.SSSz")
        st.setTimestamp(8, new Timestamp(format.parse(dp.start_time).getTime))
        st.setTimestamp(9, dp.end_time match {
          case Some(et) => new Timestamp(format.parse(et).getTime)
          case None => new Timestamp(format.parse(dp.start_time).getTime)
        })
        st.setTimestamp(10, curr_time)
        st.execute()
        st.close()
      }
    }
  }

  def insertIntoBinMonth(sensor: SensorModel, dp: DatapointModel, yyyy: Int, mm: Int, parameter: String, value: Double): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val bin_time = Some(yyyy.toString + "-" + mm.toString)
      val existing_stats = getCachedBinStatsByMonth(sensor, bin_time, bin_time, parameter, false)

      if (existing_stats.length > 0) {
        // Need to append to existing bin(s)
        existing_stats.foreach(s => {
          val (yyyy, mm, count, sum, avg, start_time, end_time) = s
          val new_count = count + 1
          val new_sum = sum + value
          val new_avg = new_sum / new_count

          val query = "update bins_month set datapoint_count = ?, sum = ?, average = ?, updated = ? " +
            "where sensor_id = ? and yyyy = ? and and mm = ? and parameter = ?;"
          val st = conn.prepareStatement(query)
          st.setInt(1, new_count)
          st.setDouble(2, new_sum)
          st.setDouble(3, new_avg)
          st.setTimestamp(4, curr_time)
          st.setInt(5, sensor.id)
          st.setInt(6, yyyy)
          st.setInt(7, mm)
          st.setString(8, parameter)
          st.execute()
          st.close()
        })

      } else {
        // Need to create a new bin
        val query = "insert into bins_month (sensor_id, yyyy, mm, parameter, datapoint_count, sum, average, start_time, end_time, updated) " +
          "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
        val st = conn.prepareStatement(query)
        st.setInt(1, sensor.id)
        st.setInt(2, yyyy)
        st.setInt(3, mm)
        st.setString(4, parameter)
        st.setInt(5, 1)
        st.setDouble(6, value)
        st.setDouble(7, value)
        val format = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss.SSSz")
        st.setTimestamp(8, new Timestamp(format.parse(dp.start_time).getTime))
        st.setTimestamp(9, dp.end_time match {
          case Some(et) => new Timestamp(format.parse(et).getTime)
          case None => new Timestamp(format.parse(dp.start_time).getTime)
        })
        st.setTimestamp(10, curr_time)
        st.execute()
        st.close()
      }
    }
  }

  def insertIntoBinDay(sensor: SensorModel, dp: DatapointModel, yyyy: Int, mm: Int, dd: Int, parameter: String, value: Double): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val bin_time = Some(yyyy.toString + "-" + mm.toString + "-" + dd.toString)
      val existing_stats = getCachedBinStatsByDay(sensor, bin_time, bin_time, parameter, false)

      if (existing_stats.length > 0) {
        // Need to append to existing bin(s)
        existing_stats.foreach(s => {
          val (yyyy, mm, dd, count, sum, avg, start_time, end_time) = s
          val new_count = count + 1
          val new_sum = sum + value
          val new_avg = new_sum / new_count

          val query = "update bins_day set datapoint_count = ?, sum = ?, average = ?, updated = ? " +
            "where sensor_id = ? and yyyy = ? and and mm = ? and dd = ? and parameter = ?;"
          val st = conn.prepareStatement(query)
          st.setInt(1, new_count)
          st.setDouble(2, new_sum)
          st.setDouble(3, new_avg)
          st.setTimestamp(4, curr_time)
          st.setInt(5, sensor.id)
          st.setInt(6, yyyy)
          st.setInt(7, mm)
          st.setInt(8, dd)
          st.setString(9, parameter)
          st.execute()
          st.close()
        })

      } else {
        // Need to create a new bin
        val query = "insert into bins_day (sensor_id, yyyy, mm, dd, parameter, datapoint_count, sum, average, start_time, end_time, updated) " +
          "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
        val st = conn.prepareStatement(query)
        st.setInt(1, sensor.id)
        st.setInt(2, yyyy)
        st.setInt(3, mm)
        st.setInt(4, dd)
        st.setString(5, parameter)
        st.setInt(6, 1)
        st.setDouble(7, value)
        st.setDouble(8, value)
        val format = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss.SSSz")
        st.setTimestamp(9, new Timestamp(format.parse(dp.start_time).getTime))
        st.setTimestamp(10, dp.end_time match {
          case Some(et) => new Timestamp(format.parse(et).getTime)
          case None => new Timestamp(format.parse(dp.start_time).getTime)
        })
        st.setTimestamp(11, curr_time)
        st.execute()
        st.close()
      }
    }
  }

  def insertIntoBinHour(sensor: SensorModel, dp: DatapointModel, yyyy: Int, mm: Int, dd: Int, hh: Int, parameter: String, value: Double): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val bin_time = Some(yyyy.toString + "-" + mm.toString + "-" + dd.toString + "T" + hh.toString + ":00:00.000")
      val existing_stats = getCachedBinStatsByHour(sensor, bin_time, bin_time, parameter, false)

      if (existing_stats.length > 0) {
        // Need to append to existing bin(s)
        existing_stats.foreach(s => {
          val (yyyy, mm, dd, hh, count, sum, avg, start_time, end_time) = s
          val new_count = count + 1
          val new_sum = sum + value
          val new_avg = new_sum / new_count

          val query = "update bins_hour set datapoint_count = ?, sum = ?, average = ?, updated = ? " +
            "where sensor_id = ? and yyyy = ? and and mm = ? and dd = ? and hh = ? and parameter = ?;"
          val st = conn.prepareStatement(query)
          st.setInt(1, new_count)
          st.setDouble(2, new_sum)
          st.setDouble(3, new_avg)
          st.setTimestamp(4, curr_time)
          st.setInt(5, sensor.id)
          st.setInt(6, yyyy)
          st.setInt(7, mm)
          st.setInt(8, dd)
          st.setInt(9, hh)
          st.setString(10, parameter)
          st.execute()
          st.close()
        })

      } else {
        // Need to create a new bin
        val query = "insert into bins_hour (sensor_id, yyyy, mm, dd, hh, parameter, datapoint_count, sum, average, start_time, end_time, updated) " +
          "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
        val st = conn.prepareStatement(query)
        st.setInt(1, sensor.id)
        st.setInt(2, yyyy)
        st.setInt(3, mm)
        st.setInt(4, dd)
        st.setInt(5, hh)
        st.setString(6, parameter)
        st.setInt(7, 1)
        st.setDouble(8, value)
        st.setDouble(9, value)
        val format = new SimpleDateFormat("yyyy-MM-ddTHH:mm:ss.SSSz")
        st.setTimestamp(10, new Timestamp(format.parse(dp.start_time).getTime))
        st.setTimestamp(11, dp.end_time match {
          case Some(et) => new Timestamp(format.parse(et).getTime)
          case None => new Timestamp(format.parse(dp.start_time).getTime)
        })
        st.setTimestamp(12, curr_time)
        st.execute()
        st.close()
      }
    }
  }

  /* Fetch existing bin statistics from cache for a specific parameter.
   *  since, until -- SQL query timestamps, e.g. '2017-12', '2013-10-38T12:57:59.923'
   *  total -- true will return a single total for all bins requested, instead of individual bins.
   */
  def getCachedBinStatsByYear(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var result = List[(Int, Int, Double, Double, Timestamp, Timestamp)]()
    var tot_count = 0
    var tot_sum = 0.0
    var tot_avg = 0.0

    val (start_year, end_year, start_month, end_month, start_day, end_day, start_hour, end_hour) = (since, until) match {
      case (Some(start_time), Some(end_time)) => DatapointsHelper.parseTimeRange(start_time, end_time)
      case (Some(start_time), None) => DatapointsHelper.parseTimeRange(start_time, sensor.max_end_time)
      case (None, Some(end_time)) => DatapointsHelper.parseTimeRange(sensor.min_start_time, end_time)
      case (None, None) => DatapointsHelper.parseTimeRange(sensor.min_start_time, sensor.max_end_time)
    }

    db.withConnection { conn =>
      val query = "select yyyy, datapoint_count, sum, average, start_time, end_time from bins_year " +
        "where sensor_id = ? and parameter = ? and yyyy >= ? and yyyy <= ?;"
      val st = conn.prepareStatement(query)

      st.setInt(1, sensor.id)
      st.setString(2, parameter)
      st.setInt(3, start_year)
      st.setInt(4, end_year)
      val stats = st.executeQuery()

      while (stats.next()) {
        tot_count += stats.getInt(2)
        tot_sum += stats.getDouble(3)
        tot_avg += stats.getDouble(4)
        result = result :+ (
          stats.getInt(1), // year
          stats.getInt(2), // count
          stats.getDouble(3), // sum
          stats.getDouble(4), // avg
          stats.getTimestamp(5), // start_time
          stats.getTimestamp(6) // end_time
        )
      }
      stats.close()
      st.close()
    }

    if (total)
      List((-1, tot_count, tot_sum, tot_avg, new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis())))
    else
      result
  }

  /* Fetch existing bin statistics from cache for a specific parameter.
 *  since, until -- SQL query timestamps, e.g. '2017-12', '2013-10-38T12:57:59.923'
 *  total -- true will return a single total for all bins requested, instead of individual bins.
 */
  def getCachedBinStatsBySeason(sensor: SensorModel, season: Option[String], since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, String, Int, Double, Double, Timestamp, Timestamp)] = {

    var result = List[(Int, String, Int, Double, Double, Timestamp, Timestamp)]()
    var tot_count = 0
    var tot_sum = 0.0
    var tot_avg = 0.0

    val (start_year, end_year, start_month, end_month, start_day, end_day, start_hour, end_hour) = (since, until) match {
      case (Some(start_time), Some(end_time)) => DatapointsHelper.parseTimeRange(start_time, end_time)
      case (Some(start_time), None) => DatapointsHelper.parseTimeRange(start_time, sensor.max_end_time)
      case (None, Some(end_time)) => DatapointsHelper.parseTimeRange(sensor.min_start_time, end_time)
      case (None, None) => DatapointsHelper.parseTimeRange(sensor.min_start_time, sensor.max_end_time)
    }

    db.withConnection { conn =>
      var query = "select yyyy, season, datapoint_count, sum, average, start_time, end_time from bins_season " +
        "where sensor_id = ? and parameter = ? and yyyy >= ? and yyyy <= ?"
      season match {
        case (Some(s)) => query += " and season = ? ;"
        case None => query += ";"
      }
      val st = conn.prepareStatement(query)

      st.setInt(1, sensor.id)
      st.setString(2, parameter)
      st.setInt(3, start_year)
      st.setInt(4, end_year)

      season match {
        case Some(s) => st.setString(5, s)
        case None =>
      }
      val stats = st.executeQuery()

      while (stats.next()) {
        tot_count += stats.getInt(3)
        tot_sum += stats.getDouble(4)
        tot_avg += stats.getDouble(5)
        result = result :+ (
          stats.getInt(1), // year
          stats.getString(2), //season
          stats.getInt(3), // count
          stats.getDouble(4), // sum
          stats.getDouble(5), // avg
          stats.getTimestamp(6), // start_time
          stats.getTimestamp(7) // end_time
        )
      }
      stats.close()
      st.close()
    }

    if (total)
      List((-1, "", tot_count, tot_sum, tot_avg, new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis())))
    else
      result
  }

  def getCachedBinStatsByMonth(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var result = List[(Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
    var tot_count = 0
    var tot_sum = 0.0
    var tot_avg = 0.0

    val (start_year, end_year, start_month, end_month, start_day, end_day, start_hour, end_hour) = (since, until) match {
      case (Some(start_time), Some(end_time)) => DatapointsHelper.parseTimeRange(start_time, end_time)
      case (Some(start_time), None) => DatapointsHelper.parseTimeRange(start_time, sensor.max_end_time)
      case (None, Some(end_time)) => DatapointsHelper.parseTimeRange(sensor.min_start_time, end_time)
      case (None, None) => DatapointsHelper.parseTimeRange(sensor.min_start_time, sensor.max_end_time)
    }

    db.withConnection { conn =>
      var st = conn.prepareStatement("")
      val stats = if (start_year != end_year) {
        /**
         * If the years aren't the same, 3 possible cases:
         * 1) start_year, check if mm,dd > start
         * 2) end_year, check if mm,dd < end
         * 3) middle years, include all mm,dd
         */
        val query = "select yyyy, mm, datapoint_count, sum, average, start_time, end_time from bins_month " +
          "where sensor_id = ? and parameter = ? and " +
          "((yyyy = ? and mm >= ?) or (yyyy = ? and mm <= ?) or (yyyy > ? and yyyy < ?));"
        st = conn.prepareStatement(query)
        st.setInt(1, sensor.id)
        st.setString(2, parameter)
        st.setInt(3, start_year)
        st.setInt(4, start_month)
        st.setInt(5, end_year)
        st.setInt(6, end_month)
        st.setInt(7, start_year)
        st.setInt(8, end_year)
        st.executeQuery()
      } else {
        val query = "select yyyy, mm, datapoint_count, sum, average, start_time, end_time from bins_month " +
          "where sensor_id = ? and parameter = ? and (yyyy = ? and mm >= ? and mm <= ?);"
        st = conn.prepareStatement(query)
        st.setInt(1, sensor.id)
        st.setString(2, parameter)
        st.setInt(3, start_year)
        st.setInt(4, start_month)
        st.setInt(5, end_month)
        st.executeQuery()
      }

      while (stats.next()) {
        tot_count += stats.getInt(3)
        tot_sum += stats.getDouble(4)
        tot_avg += stats.getDouble(5)
        result = result :+ (
          stats.getInt(1), // year
          stats.getInt(2), // month
          stats.getInt(3), // count
          stats.getDouble(4), // sum
          stats.getDouble(5), // avg
          stats.getTimestamp(6), // start_time
          stats.getTimestamp(7) // end_time
        )
      }
      stats.close()
      st.close()
    }
    if (total)
      List((-1, -1, tot_count, tot_sum, tot_avg, new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis())))
    else
      result
  }

  def getCachedBinStatsByDay(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var result = List[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
    var tot_count = 0
    var tot_sum = 0.0
    var tot_avg = 0.0

    val (start_year, end_year, start_month, end_month, start_day, end_day, start_hour, end_hour) = (since, until) match {
      case (Some(start_time), Some(end_time)) => DatapointsHelper.parseTimeRange(start_time, end_time)
      case (Some(start_time), None) => DatapointsHelper.parseTimeRange(start_time, sensor.max_end_time)
      case (None, Some(end_time)) => DatapointsHelper.parseTimeRange(sensor.min_start_time, end_time)
      case (None, None) => DatapointsHelper.parseTimeRange(sensor.min_start_time, sensor.max_end_time)
    }

    db.withConnection { conn =>
      var st = conn.prepareStatement("")

      val stats = if (start_year != end_year) {
        val query = "select yyyy, mm, dd, datapoint_count, sum, average, start_time, end_time from bins_day " +
          "where sensor_id = ? and parameter = ? and (" +
          // start_year subclause -- special check for start_month+start_day, then get everything after
          " (yyyy = ? and ((mm = ? and dd >= ?) or (mm > ?))) or " +
          // end_year subclause -- special check for end_month+end_day, then get everything before
          " (yyyy = ? and ((mm = ? and dd <= ?) or (mm < ?))) or " +
          // between subclause -- get everything for these months
          " (yyyy > ? and yyyy < ?));"
        st = conn.prepareStatement(query)
        st.setInt(1, sensor.id)
        st.setString(2, parameter)
        // start_year subclause
        st.setInt(3, start_year)
        st.setInt(4, start_month)
        st.setInt(5, start_day)
        st.setInt(6, start_month)
        // end_year subclause
        st.setInt(7, end_year)
        st.setInt(8, end_month)
        st.setInt(9, end_day)
        st.setInt(10, end_month)
        // between subclause
        st.setInt(11, start_year)
        st.setInt(12, end_year)
        st.executeQuery()
      } else {
        val query = "select yyyy, mm, dd, datapoint_count, sum, average, start_time, end_time from bins_day " +
          "where sensor_id = ? and parameter = ? and yyyy = ? and " +
          // for single year, we just need to check start_month and end_month days and get everything in-between
          "((mm = ? and dd >= ?) or (mm = ? and dd <= ?) or (mm > ? and mm < ?));"
        st = conn.prepareStatement(query)
        st.setInt(1, sensor.id)
        st.setString(2, parameter)
        st.setInt(3, start_year)
        st.setInt(4, start_month)
        st.setInt(5, start_day)
        st.setInt(6, end_month)
        st.setInt(7, end_day)
        st.setInt(8, start_month)
        st.setInt(9, end_month)
        st.executeQuery()
      }

      while (stats.next()) {
        tot_count += stats.getInt(3)
        tot_sum += stats.getDouble(4)
        tot_avg += stats.getDouble(5)
        result = result :+ (
          stats.getInt(1), // year
          stats.getInt(2), // month
          stats.getInt(3), // day
          stats.getInt(4), // count
          stats.getDouble(5), // sum
          stats.getDouble(6), // avg
          stats.getTimestamp(7), // start_time
          stats.getTimestamp(8) // end_time
        )
      }
      stats.close()
      st.close()
    }
    if (total)
      List((-1, -1, -1, tot_count, tot_sum, tot_avg, new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis())))
    else
      result
  }

  def getCachedBinStatsByHour(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var result = List[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
    var tot_count = 0
    var tot_sum = 0.0
    var tot_avg = 0.0

    val (start_year, end_year, start_month, end_month, start_day, end_day, start_hour, end_hour) = (since, until) match {
      case (Some(start_time), Some(end_time)) => DatapointsHelper.parseTimeRange(start_time, end_time)
      case (Some(start_time), None) => DatapointsHelper.parseTimeRange(start_time, sensor.max_end_time)
      case (None, Some(end_time)) => DatapointsHelper.parseTimeRange(sensor.min_start_time, end_time)
      case (None, None) => DatapointsHelper.parseTimeRange(sensor.min_start_time, sensor.max_end_time)
    }

    db.withConnection { conn =>
      var st = conn.prepareStatement("")
      val stats = if (start_year != end_year) {
        val query = "select yyyy, mm, dd, hh, datapoint_count, sum, average, start_time, end_time from bins_hour " +
          "where sensor_id = ? and parameter = ? and (" +
          // start_year subclause -- special check for start_month+start_day+start_hour, then get everything after
          " (yyyy = ? and ((mm = ? and dd = ? and hh >= ?) or (mm = ? and dd > ?) or (mm > ?))) or " +
          // end_year subclause -- special check for end_month+end_day+end_hour, then get everything before
          " (yyyy = ? and ((mm = ? and dd = ? and hh <= ?) or (mm = ? and dd < ?) or (mm < ?))) or " +
          // between subclause -- get everything for these months
          " (yyyy > ? and yyyy < ?));"
        st = conn.prepareStatement(query)
        st.setInt(1, sensor.id)
        st.setString(2, parameter)
        // start_year subclause
        st.setInt(3, start_year)
        st.setInt(4, start_month)
        st.setInt(5, start_day)
        st.setInt(6, start_hour)
        st.setInt(7, start_month)
        st.setInt(8, start_day)
        st.setInt(9, start_month)
        // end_year subclause
        st.setInt(10, end_year)
        st.setInt(11, end_month)
        st.setInt(12, end_day)
        st.setInt(13, end_hour)
        st.setInt(14, end_month)
        st.setInt(15, end_day)
        st.setInt(16, end_month)
        // between subclause
        st.setInt(17, start_year)
        st.setInt(18, end_year)
        st.executeQuery()
      } else {
        val query = "select yyyy, mm, dd, hh, datapoint_count, sum, average, start_time, end_time from bins_hour " +
          "where sensor_id = ? and parameter = ? and yyyy = ? and (" +
          // for single year, we just need to check start_month and end_month days+hours and get everything in-between
          " (mm = ? and dd = ? and hh >= ?) or (mm = ? and dd > ?) or " +
          " (mm = ? and dd = ? and hh <= ?) or (mm = ? and dd < ?) or " +
          " (mm > ? and mm < ?));"
        st = conn.prepareStatement(query)
        st.setInt(1, sensor.id)
        st.setString(2, parameter)
        st.setInt(3, start_year)
        // start_month subclause
        st.setInt(4, start_month)
        st.setInt(5, start_day)
        st.setInt(6, start_hour)
        st.setInt(7, start_month)
        st.setInt(8, start_day)
        // end_month subclause
        st.setInt(9, end_month)
        st.setInt(10, end_day)
        st.setInt(11, end_hour)
        st.setInt(12, end_month)
        st.setInt(13, end_day)
        // between subclause
        st.setInt(14, start_month)
        st.setInt(15, end_month)
        st.executeQuery()
      }

      while (stats.next()) {
        tot_count += stats.getInt(3)
        tot_sum += stats.getDouble(4)
        tot_avg += stats.getDouble(5)
        result = result :+ (
          stats.getInt(1), // year
          stats.getInt(2), // month
          stats.getInt(3), // day
          stats.getInt(4), // hour
          stats.getInt(5), // count
          stats.getDouble(6), // sum
          stats.getDouble(7), // avg
          stats.getTimestamp(8), // start_time
          stats.getTimestamp(9) // end_time
        )
      }
      stats.close()
      st.close()
    }
    if (total)
      List((-1, -1, -1, -1, tot_count, tot_sum, tot_avg, new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis())))
    else
      result
  }

  def getCountBinsByYear(sensor_id: Option[Int]): Int = {
    var output = 0
    db.withConnection { conn =>
      var query = "SELECT COUNT(*) FROM bins_year"
      sensor_id match {
        case Some(id) => {
          query += " WHERE sensor_id = ?"
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

  def getCountBinsBySeason(sensor_id: Option[Int]): Int = {
    var output = 0
    db.withConnection { conn =>
      var query = "SELECT COUNT(*) FROM bins_season"
      sensor_id match {
        case Some(id) => {
          query += " WHERE sensor_id = ?"
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

  def getCountBinsByMonth(sensor_id: Option[Int]): Int = {
    var output = 0
    db.withConnection { conn =>
      var query = "SELECT COUNT(*) FROM bins_month"
      sensor_id match {
        case Some(id) => {
          query += " WHERE sensor_id = ?"
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

  def getCountBinsByDay(sensor_id: Option[Int]): Int = {
    var output = 0
    db.withConnection { conn =>
      var query = "SELECT COUNT(*) FROM bins_day"
      sensor_id match {
        case Some(id) => {
          query += " WHERE sensor_id = ?"
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

  def getCountBinsByHour(sensor_id: Option[Int]): Int = {
    var output = 0
    db.withConnection { conn =>
      var query = "SELECT COUNT(*) FROM bins_hour"
      sensor_id match {
        case Some(id) => {
          query += " WHERE sensor_id = ?"
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
