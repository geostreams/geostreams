package db

import java.sql.Timestamp
import com.google.inject.ImplementedBy
import db.postgres.PostgresCache
import models.{ SensorModel, DatapointModel }

/**
 * Access Cache store.
 */
@ImplementedBy(classOf[PostgresCache])
trait Cache {
  def calculateBinsByYear(sensor_id: Int, since: Option[String], until: Option[String], parameter: String)

  def calculateBinsByMonth(sensor_id: Int, since: Option[String], until: Option[String], parameter: String)

  def calculateBinsByDay(sensor_id: Int, since: Option[String], until: Option[String], parameter: String)

  def calculateBinsByHour(sensor_id: Int, since: Option[String], until: Option[String], parameter: String)

  def insertIntoBinYear(sensor: SensorModel, dp: DatapointModel, yyyy: Int, parameter: String, value: Double)

  def insertIntoBinMonth(sensor: SensorModel, dp: DatapointModel, yyyy: Int, mm: Int, parameter: String, value: Double)

  def insertIntoBinDay(sensor: SensorModel, dp: DatapointModel, yyyy: Int, mm: Int, dd: Int, parameter: String, value: Double)

  def insertIntoBinHour(sensor: SensorModel, dp: DatapointModel, yyyy: Int, mm: Int, dd: Int, hh: Int, parameter: String, value: Double)

  def getCachedBinStatsByYear(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Double, Double, Timestamp, Timestamp)]

  def getCachedBinStatsByMonth(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Int, Double, Double, Timestamp, Timestamp)]

  def getCachedBinStatsByDay(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]

  def getCachedBinStatsByHour(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]

}
