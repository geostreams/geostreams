package db

import java.sql.Timestamp

import com.google.inject.ImplementedBy
import db.postgres.PostgresCache

/**
 * Access Cache store.
 */
@ImplementedBy(classOf[PostgresCache])
trait Cache {
  def createOrUpdateBinStatsByYear(
    sensor_id: Int,
    start_year: Option[Int], end_year: Option[Int],
    parameter: String
  )

  def createOrUpdateBinStatsByMonth(
    sensor_id: Int,
    start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    parameter: String
  )

  def createOrUpdateBinStatsByDay(
    sensor_id: Int,
    start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int],
    parameter: String
  )

  def createOrUpdateBinStatsByHour(
    sensor_id: Int,
    start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int], start_hour: Option[Int], end_hour: Option[Int],
    parameter: String
  )

  def getCachedBinStatsByYear(
    sensor_id: Int,
    start_year: Option[Int], end_year: Option[Int],
    parameter: String, total: Boolean
  ): List[(Int, Int, Double, Double, Timestamp, Timestamp)]

  def getCachedBinStatsByMonth(
    sensor_id: Int,
    start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    parameter: String, total: Boolean
  ): List[(Int, Int, Int, Double, Double, Timestamp, Timestamp)]

  def getCachedBinStatsByDay(
    sensor_id: Int,
    start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int],
    parameter: String, total: Boolean
  ): List[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]

  def getCachedBinStatsByHour(
    sensor_id: Int,
    start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int], start_hour: Option[Int], end_hour: Option[Int],
    parameter: String, total: Boolean
  ): List[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]

}
