package db.postgres

import java.sql.{ Timestamp }
import akka.actor.ActorSystem
import db.{ Sensors, Cache }
import javax.inject.Inject
import play.api.Logger
import play.api.db.Database

import scala.collection.mutable.ListBuffer

/**
 * Store aggregate statistics in cache bins to speed up retrieval.
 */
class PostgresCache @Inject() (db: Database, sensors: Sensors, actSys: ActorSystem) extends Cache {

  // DO AGGREGATE CALCULATION FOR BINS
  def calculateBinStatsByYear(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], parameter: String): List[(Int, Int, Double, Double, Timestamp, Timestamp)] = {

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

      query += start_year.fold("")(n => " and extract(year from datapoints.start_time) >= ?")
      query += end_year.fold("")(n => " and extract(year from datapoints.start_time) <= ?")
      query += " group by yyyy;"
      val st = conn.prepareStatement(query)

      st.setString(1, parameter)
      st.setString(2, parameter)
      st.setString(3, parameter)
      st.setInt(4, sensor_id)
      var i = 5
      start_year match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      end_year match {
        case Some(n) => {
          st.setInt(i, n)
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

  def calculateBinStatsByMonth(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int], parameter: String): List[(Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

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
      query += start_year.fold("")(n => " and extract(year from datapoints.start_time) >= ?")
      query += end_year.fold("")(n => " and extract(year from datapoints.start_time) <= ?")
      query += start_month.fold("")(n => " and extract(month from datapoints.start_time) >= ?")
      query += end_month.fold("")(n => " and extract(month from datapoints.start_time) <= ?")
      query += " group by yyyy, mm;"
      val st = conn.prepareStatement(query)

      st.setString(1, parameter)
      st.setString(2, parameter)
      st.setString(3, parameter)
      st.setInt(4, sensor_id)
      var i = 5
      start_year match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      end_year match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      start_month match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      end_month match {
        case Some(n) => {
          st.setInt(i, n)
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

  def calculateBinStatsByDay(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int], parameter: String): List[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

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
      query += start_year.fold("")(n => " and extract(year from datapoints.start_time) >= ?")
      query += end_year.fold("")(n => " and extract(year from datapoints.start_time) <= ?")
      query += start_month.fold("")(n => " and extract(month from datapoints.start_time) >= ?")
      query += end_month.fold("")(n => " and extract(month from datapoints.start_time) <= ?")
      query += start_day.fold("")(n => " and extract(day from datapoints.start_time) >= ?")
      query += end_day.fold("")(n => " and extract(day from datapoints.start_time) <= ?")
      query += " group by yyyy, mm, dd;"
      val st = conn.prepareStatement(query)

      st.setString(1, parameter)
      st.setString(2, parameter)
      st.setString(3, parameter)
      st.setInt(4, sensor_id)
      var i = 5
      start_year match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      end_year match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      start_month match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      end_month match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      start_day match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      end_day match {
        case Some(n) => {
          st.setInt(i, n)
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

  def calculateBinStatsByHour(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int], start_hour: Option[Int], end_hour: Option[Int], parameter: String): List[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

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
      query += start_year.fold("")(n => " and extract(year from datapoints.start_time) >= ?")
      query += end_year.fold("")(n => " and extract(year from datapoints.start_time) <= ?")
      query += start_month.fold("")(n => " and extract(month from datapoints.start_time) >= ?")
      query += end_month.fold("")(n => " and extract(month from datapoints.start_time) <= ?")
      query += start_day.fold("")(n => " and extract(day from datapoints.start_time) >= ?")
      query += end_day.fold("")(n => " and extract(day from datapoints.start_time) <= ?")
      query += start_hour.fold("")(n => " and extract(hour from datapoints.start_time) >= ?")
      query += end_hour.fold("")(n => " and extract(hour from datapoints.start_time) <= ?")
      query += " group by yyyy, mm, dd, hh;"
      val st = conn.prepareStatement(query)

      st.setString(1, parameter)
      st.setString(2, parameter)
      st.setString(3, parameter)
      st.setInt(4, sensor_id)
      var i = 5
      start_year match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      end_year match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      start_month match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      end_month match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      start_day match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      end_day match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      start_hour match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
        case None => {}
      }
      end_hour match {
        case Some(n) => {
          st.setInt(i, n)
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

  // FETCH BIN CALCULATIONS AND STORE IN CACHE
  def createOrUpdateBinStatsByYear(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], parameter: String): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val stats_values = calculateBinStatsByYear(sensor_id, start_year, end_year, parameter)
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

  def createOrUpdateBinStatsByMonth(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int], parameter: String): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val stats_values = calculateBinStatsByMonth(sensor_id, start_year, end_year, start_month, end_month, parameter)
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

  def createOrUpdateBinStatsByDay(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int], parameter: String): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val stats_values = calculateBinStatsByDay(sensor_id, start_year, end_year, start_month, end_month, start_day, end_day, parameter)

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

  def createOrUpdateBinStatsByHour(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int], start_hour: Option[Int], end_hour: Option[Int], parameter: String): Unit = {
    db.withConnection { conn =>
      val curr_time = new Timestamp(System.currentTimeMillis())
      val stats_values = calculateBinStatsByHour(sensor_id, start_year, end_year, start_month, end_month,
        start_day, end_day, start_hour, end_hour, parameter)

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

  // GET BIN DATA FROM CACHE
  def getCachedBinStatsByYear(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], parameter: String, total: Boolean): List[(Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var result = List[(Int, Int, Double, Double, Timestamp, Timestamp)]()
    var tot_count = 0
    var tot_sum = 0.0
    var tot_avg = 0.0

    db.withConnection { conn =>
      var query = "select yyyy, datapoint_count, sum, average, start_time, end_time from bins_year " +
        "where sensor_id = ? and parameter = ?"
      query += start_year.fold("")(sy => " and yyyy >= ?")
      query += end_year.fold("")(ey => " and yyyy <= ?")
      val st = conn.prepareStatement(query)

      st.setInt(1, sensor_id)
      st.setString(2, parameter)
      var i = 3
      start_year match {
        case Some(sy) => {
          st.setInt(i, sy)
          i += 1
        }
      }
      end_year match {
        case Some(ey) => {
          st.setInt(i, ey)
          i += 1
        }
      }
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

  def getCachedBinStatsByMonth(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int], parameter: String, total: Boolean): List[(Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var result = List[(Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
    var tot_count = 0
    var tot_sum = 0.0
    var tot_avg = 0.0

    db.withConnection { conn =>
      // FIRST, BUILD QUERY STRING
      var query = "select yyyy, mm, datapoint_count, sum, average, start_time, end_time from bins_month " +
        "where sensor_id = ? and parameter = ?"
      query += start_year.fold("")(sy => " and yyyy >= ?")
      query += end_year.fold("")(ey => " and yyyy <= ?")
      query += start_month.fold("")(sm => " and mm >= ?")
      query += end_month.fold("")(em => " and mm <= ?")
      val st = conn.prepareStatement(query)

      // NEXT USE SAME LOGIC TO POPULATE ? VALUES
      st.setInt(1, sensor_id)
      st.setString(2, parameter)
      var i = 3
      start_year match {
        case Some(sy) => {
          st.setInt(i, sy)
          i += 1
        }
      }
      end_year match {
        case Some(ey) => {
          st.setInt(i, ey)
          i += 1
        }
      }
      start_month match {
        case Some(sm) => {
          st.setInt(i, sm)
          i += 1
        }
      }
      end_month match {
        case Some(em) => {
          st.setInt(i, em)
          i += 1
        }
      }

      val stats = st.executeQuery()
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

  def getCachedBinStatsByDay(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int], parameter: String, total: Boolean): List[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var result = List[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
    var tot_count = 0
    var tot_sum = 0.0
    var tot_avg = 0.0

    db.withConnection { conn =>
      // FIRST, BUILD QUERY STRING
      var query = "select yyyy, mm, dd, datapoint_count, sum, average, start_time, end_time from bins_day " +
        "where sensor_id = ? and parameter = ?"
      query += start_year.fold("")(n => " and yyyy >= ?")
      query += end_year.fold("")(n => " and yyyy <= ?")
      query += start_month.fold("")(n => " and mm >= ?")
      query += end_month.fold("")(n => " and mm <= ?")
      query += start_day.fold("")(n => " and dd >= ?")
      query += end_day.fold("")(n => " and dd <= ?")
      val st = conn.prepareStatement(query)

      // NEXT USE SAME LOGIC TO POPULATE ? VALUES
      st.setInt(1, sensor_id)
      st.setString(2, parameter)
      var i = 3
      start_year match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      end_year match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      start_month match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      end_month match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      start_day match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      end_day match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }

      val stats = st.executeQuery()
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

  def getCachedBinStatsByHour(sensor_id: Int, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int], start_hour: Option[Int], end_hour: Option[Int], parameter: String, total: Boolean): List[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)] = {

    var result = List[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]()
    var tot_count = 0
    var tot_sum = 0.0
    var tot_avg = 0.0

    db.withConnection { conn =>
      // FIRST, BUILD QUERY STRING
      var query = "select yyyy, mm, dd, hh, datapoint_count, sum, average, start_time, end_time from bins_hour " +
        "where sensor_id = ? and parameter = ?"
      query += start_year.fold("")(n => " and yyyy >= ?")
      query += end_year.fold("")(n => " and yyyy <= ?")
      query += start_month.fold("")(n => " and mm >= ?")
      query += end_month.fold("")(n => " and mm <= ?")
      query += start_day.fold("")(n => " and dd >= ?")
      query += end_day.fold("")(n => " and dd <= ?")
      query += start_hour.fold("")(n => " and hh >= ?")
      query += end_hour.fold("")(n => " and hh <= ?")
      val st = conn.prepareStatement(query)

      // NEXT USE SAME LOGIC TO POPULATE ? VALUES
      st.setInt(1, sensor_id)
      st.setString(2, parameter)
      var i = 3
      start_year match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      end_year match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      start_month match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      end_month match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      start_day match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      end_day match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      start_hour match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }
      end_hour match {
        case Some(n) => {
          st.setInt(i, n)
          i += 1
        }
      }

      val stats = st.executeQuery()
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

}
