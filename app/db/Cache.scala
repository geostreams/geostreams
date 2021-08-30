package db

import java.sql.Timestamp
import com.google.inject.ImplementedBy
import db.postgres.PostgresCache
import models.{ DatapointModel, SensorModel }
import play.api.libs.json.{ JsValue }

/**
 * Access Cache store.
 */
@ImplementedBy(classOf[PostgresCache])
trait Cache {
  def calculateBinsByYear(sensor_id: Int, since: Option[String], until: Option[String], parameter: String)

  def calculateBinsBySeason(sensor_id: Int, since: Option[String], until: Option[String], parameter: String)

  def calculateBinsByMonth(sensor_id: Int, since: Option[String], until: Option[String], parameter: String)

  def calculateBinsByDay(sensor_id: Int, since: Option[String], until: Option[String], parameter: String)

  def calculateBinsByHour(sensor_id: Int, since: Option[String], until: Option[String], parameter: String)

  def insertIntoBinYear(sensor: SensorModel, dp: DatapointModel, yyyy: Int, parameter: String, value: Double)

  def insertIntoBinSeason(sensor: SensorModel, dp: DatapointModel, yyyy: Int, season: String, parameter: String, value: Double)

  def insertIntoBinMonth(sensor: SensorModel, dp: DatapointModel, yyyy: Int, mm: Int, parameter: String, value: Double)

  def insertIntoBinDay(sensor: SensorModel, dp: DatapointModel, yyyy: Int, mm: Int, dd: Int, parameter: String, value: Double)

  def insertIntoBinHour(sensor: SensorModel, dp: DatapointModel, yyyy: Int, mm: Int, dd: Int, hh: Int, parameter: String, value: Double)

  def getCachedBinStatsByYear(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Double, Double, Timestamp, Timestamp)]

  def getCachedBinStatsBySeason(sensor: SensorModel, season: Option[String], since: Option[String], until: Option[String], parameter: String, total: Boolean, sumNested: Boolean = false): List[(Int, String, Int, Double, Double, Timestamp, Timestamp)]

  def getCachedArrayBinStatsBySeason(sensor: SensorModel, season: Option[String], since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, String, Int, Map[String, Double], Timestamp, Timestamp)]

  def getCachedBinStatsByMonth(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Int, Double, Double, Timestamp, Timestamp)]

  def getCachedBinStatsByDay(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]

  def getCachedArrayBinStatsByDay(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Int, Int, JsValue, JsValue, Timestamp, Timestamp)]

  def getCachedBinStatsByHour(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, total: Boolean): List[(Int, Int, Int, Int, Int, Double, Double, Timestamp, Timestamp)]

  def getCountBinsByYear(sensor_id: Option[Int]): Int

  def getCountBinsBySeason(sensor_id: Option[Int]): Int

  def getCountBinsByMonth(sensor_id: Option[Int]): Int

  def getCountBinsByDay(sensor_id: Option[Int]): Int

  def getCountBinsByHour(sensor_id: Option[Int]): Int

}
