package controllers

import javax.inject.{ Inject, Singleton }
import java.text.DateFormatSymbols

import com.mohiva.play.silhouette.api.Silhouette
import db._
import models.SensorModel
import org.joda.time.DateTime
import play.api.{ Configuration, Logger }
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.{ JsObject, JsValue, Json }
import play.api.mvc.{ Action, Controller }
import utils.silhouette._

import scala.collection.mutable.ListBuffer

/**
 * Caches are kept to retrieve binned versions of the data (datatapoints) and trends.
 */
@Singleton
class CacheController @Inject() (val silhouette: Silhouette[TokenEnv], sensorDB: Sensors, datapointDB: Datapoints,
  cacheDB: Cache, conf: Configuration)(implicit val messagesApi: MessagesApi)
    extends AuthTokenController with I18nSupport {

  /*
   * Calculate bin values and create or update entries in bin cache tables.
   * If no sensor_id is provided, all sensors will be updated.
   * Unless restrict is disabled, only the most precise bin will be updated.
   *    e.g. if day, month, year specified:
   *      restrict true  = only 1 day bin will be updated.
   *      restrict false = 24 hour bins, 1 day bin, 1 month bin, 1 year bin will be updated.
   */
  def createOrUpdateBins(sensor_id: Option[Int], year: Option[Int], month: Option[Int], day: Option[Int],
    hour: Option[Int], parameter: Option[String], restrict: Boolean) = SecuredAction(WithService("master")) {
    sensor_id match {
      case Some(sid) => {
        Logger.debug("Creating or updating all bins for sensor_id " + sid)
        sensorDB.getSensor(sid) match {
          case Some(sensor) => {
            updateSensorStats(sensor, year, month, day, hour, parameter, restrict)
            Ok(Json.obj("status" -> "OK"))
          }
          case None => BadRequest(Json.obj("status" -> "sensor not found"))
        }
      }
      case None => {
        val allSensors = sensorDB.searchSensors(None, None)
        Logger.debug("updating " + allSensors.length.toString + " sensors")
        allSensors.foreach(sensor => {
          updateSensorStats(sensor, year, month, day, hour, parameter, restrict)
        })

        Ok(Json.obj("status" -> "OK", "sensors" -> allSensors.length.toString))
      }
    }
  }

  // Get statistics from bin cache
  def getCachedBinStatsYear(sensor_id: Int, year: Option[Int], updateBins: Boolean, parameter: Option[String]) =
    UserAwareAction { implicit request =>
      sensorDB.getSensor(sensor_id) match {
        case Some(sensor) => {

          val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
          val (start_year, end_year, _, _, _, _, _, _) = parseTimeRange(sensor.min_start_time, sensor.max_end_time)

          var result = Json.obj()

          for (p <- sensorObjectParameters) {
            val sources = sensorDB.getSensorSources(sensor_id, p)
            year match {
              case Some(yr) => {
                // specific year
                if (updateBins && request.identity.isDefined)
                  cacheDB.createOrUpdateBinStatsByYear(sensor.id, year, year, p)

                val stats_list = cacheDB.getCachedBinStatsByYear(sensor.id, year, year, p, false)

                stats_list.foreach(s => {
                  val (yyyy, count, sum, avg, start_time, end_time) = s
                  result += (p -> Json.obj("count" -> count, "sum" -> sum, "avg" -> avg,
                    "start" -> start_time.toLocalDateTime, "end" -> end_time.toLocalDateTime,
                    "label" -> yyyy.toString, "sources" -> sources))
                })
              }
              case None => {
                // all years
                if (updateBins && request.identity.isDefined)
                  cacheDB.createOrUpdateBinStatsByYear(sensor.id, Some(start_year), Some(end_year), p)

                var current_parameter_bins: ListBuffer[JsValue] = ListBuffer.empty[JsValue]
                val stats = cacheDB.getCachedBinStatsByYear(sensor.id, Some(start_year), Some(end_year), p, false)

                stats.foreach(s => {
                  val (yyyy, count, sum, avg, start_time, end_time) = s
                  current_parameter_bins += Json.obj("count" -> count, "sum" -> sum, "avg" -> avg,
                    "start" -> start_time.toLocalDateTime, "end" -> end_time.toLocalDateTime,
                    "label" -> yyyy.toString, "sources" -> sources)
                })
                result += (p -> Json.toJson(current_parameter_bins))
              }
            }
          }
          Ok(Json.obj("name" -> sensor.name, "id" -> sensor.id, "properties" -> result))
        }
        case None => BadRequest(Json.obj("status" -> "sensor not found"))
      }
    }

  def getCachedBinStatsMonth(sensor_id: Int, year: Option[Int], month: Option[Int], updateBins: Boolean,
    parameter: Option[String]) = UserAwareAction { implicit request =>
    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {

        val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
        val (start_year, end_year, _, _, _, _, _, _) = parseTimeRange(sensor.min_start_time, sensor.max_end_time)
        var result = Json.obj()

        for (p <- sensorObjectParameters) {
          val sources = sensorDB.getSensorSources(sensor_id, p)
          month match {
            case Some(mm) => {
              year match {
                case Some(yyyy) =>
                  // specific month and year
                  result += (p -> buildJsonBinsMonth(sensor, year, year, month, month, p, sources, updateBins))
                case None =>
                  // specific month, all years
                  result += (p -> buildJsonBinsMonth(sensor, Some(start_year), Some(end_year), month, month, p, sources, updateBins))
              }
            }
            case None => {
              year match {
                case Some(yyyy) =>
                  // specific year, all months
                  result += (p -> buildJsonBinsMonth(sensor, year, year, Some(1), Some(12), p, sources, updateBins))
                case None =>
                  // all months, all years
                  result += (p -> buildJsonBinsMonth(sensor, Some(start_year), Some(end_year), Some(1), Some(12), p, sources, updateBins))
              }
            }
          }
        }
        Ok(Json.obj(
          "name" -> sensor.name,
          "id" -> sensor.id,
          "properties" -> result
        ))
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  def getCachedBinStatsDay(sensor_id: Int, year: Option[Int], month: Option[Int], day: Option[Int], updateBins: Boolean,
    parameter: Option[String]) = Action {
    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {

        val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
        val (start_year, end_year, _, _, _, _, _, _) =
          parseTimeRange(sensor.min_start_time, sensor.max_end_time)
        var result = Json.obj()

        for (p <- sensorObjectParameters) {
          val sources = sensorDB.getSensorSources(sensor_id, p)
          day match {
            case Some(dd) => {
              month match {
                case Some(mm) => {
                  year match {
                    case Some(yyyy) =>
                      // specific day and month and year
                      result += (p -> buildJsonBinsDay(sensor, year, year, month, month, day, day, p, sources, updateBins))
                    case None =>
                      // specific day and month, all years
                      result += (p -> buildJsonBinsDay(sensor, Some(start_year), Some(end_year), day, day, month, month, p, sources, updateBins))
                  }
                }
                case None => {
                  year match {
                    case Some(yyyy) =>
                      // specific day and year, all months
                      result += (p -> buildJsonBinsDay(sensor, year, year, Some(1), Some(12), day, day, p, sources, updateBins))
                    case None =>
                      // specific day, all months, all years
                      result += (p -> buildJsonBinsDay(sensor, Some(start_year), Some(end_year), Some(1), Some(12), day, day, p, sources, updateBins))
                  }
                }
              }
            }
            case None => {
              month match {
                case Some(mm) => {
                  year match {
                    case Some(yyyy) =>
                      // specific month and year, all days
                      result += (p -> buildJsonBinsDay(sensor, year, year, month, month, Some(1), Some(31), p, sources, updateBins))
                    case None =>
                      // specific month, all days, all years
                      result += (p -> buildJsonBinsDay(sensor, Some(start_year), Some(end_year), month, month, Some(1), Some(31), p, sources, updateBins))
                  }
                }
                case None => {
                  year match {
                    case Some(yyyy) =>
                      // specific year, all days, all months
                      result += (p -> buildJsonBinsDay(sensor, year, year, Some(1), Some(12), Some(1), Some(31), p, sources, updateBins))
                    case None =>
                      // all days, all months, all years
                      result += (p -> buildJsonBinsDay(sensor, Some(start_year), Some(end_year), Some(1), Some(12), Some(1), Some(31), p, sources, updateBins))
                  }
                }
              }
            }
          }
        }
        Ok(Json.obj(
          "name" -> sensor.name,
          "id" -> sensor.id,
          "properties" -> result
        ))
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  def getCachedBinStatsHour(sensor_id: Int, year: Option[Int], month: Option[Int], day: Option[Int], hour: Option[Int],
    updateBins: Boolean, parameter: Option[String]) = Action {
    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {
        val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
        val (start_year, end_year, _, _, _, _, _, _) = parseTimeRange(sensor.min_start_time, sensor.max_end_time)
        var result = Json.obj()

        for (p <- sensorObjectParameters) {
          val sources = sensorDB.getSensorSources(sensor_id, p)
          hour match {
            case Some(hh) => {
              day match {
                case Some(dd) => {
                  month match {
                    case Some(mm) => {
                      year match {
                        case Some(yyyy) =>
                          // specific hour and day and month and year
                          result += (p -> buildJsonBinsHour(sensor, year, year, month, month, day, day, hour, hour, p, sources, updateBins))
                        case None =>
                          // specific hour and day and month, all years
                          result += (p -> buildJsonBinsHour(sensor, Some(start_year), Some(end_year), month, month, day, day, hour, hour, p, sources, updateBins))
                      }
                    }
                    case None => {
                      year match {
                        case Some(yyyy) =>
                          // specific hour and day and year, all months
                          result += (p -> buildJsonBinsHour(sensor, year, year, Some(1), Some(12), day, day, hour, hour, p, sources, updateBins))
                        case None =>
                          // specific hour and day, all months, all years
                          result += (p -> buildJsonBinsHour(sensor, Some(start_year), Some(end_year), Some(1), Some(12), day, day, hour, hour, p, sources, updateBins))
                      }
                    }
                  }
                }
                case None => {
                  month match {
                    case Some(mm) => {
                      year match {
                        case Some(yyyy) =>
                          // specific hour and month and year, all days
                          result += (p -> buildJsonBinsHour(sensor, year, year, month, month, Some(1), Some(31), hour, hour, p, sources, updateBins))
                        case None =>
                          // specific hour and month, all days, all years
                          result += (p -> buildJsonBinsHour(sensor, Some(start_year), Some(end_year), month, month, Some(1), Some(31), hour, hour, p, sources, updateBins))
                      }
                    }
                    case None => {
                      year match {
                        case Some(yyyy) =>
                          // specific hour and year, all days, all months
                          result += (p -> buildJsonBinsHour(sensor, year, year, Some(1), Some(12), Some(1), Some(31), hour, hour, p, sources, updateBins))
                        case None =>
                          // specific hour, all days, all months, all years
                          result += (p -> buildJsonBinsHour(sensor, Some(start_year), Some(end_year), Some(1), Some(12), Some(1), Some(31), hour, hour, p, sources, updateBins))
                      }
                    }
                  }
                }
              }
            }
            case None => {
              day match {
                case Some(dd) => {
                  month match {
                    case Some(mm) => {
                      year match {
                        case Some(yyyy) =>
                          // specific day and month and year, all hours
                          result += (p -> buildJsonBinsHour(sensor, year, year, month, month, day, day, Some(0), Some(23), p, sources, updateBins))
                        case None =>
                          // specific day and month, all hours, all years
                          result += (p -> buildJsonBinsHour(sensor, Some(start_year), Some(end_year), day, day, Some(0), Some(23), month, month, p, sources, updateBins))
                      }
                    }
                    case None => {
                      year match {
                        case Some(yyyy) =>
                          // specific day and year, all hours, all months
                          result += (p -> buildJsonBinsHour(sensor, year, year, Some(1), Some(12), day, day, Some(0), Some(23), p, sources, updateBins))
                        case None =>
                          // specific day, all hours, all months, all years
                          result += (p -> buildJsonBinsHour(sensor, Some(start_year), Some(end_year), Some(1), Some(12), day, day, Some(0), Some(23), p, sources, updateBins))
                      }
                    }
                  }
                }
                case None => {
                  month match {
                    case Some(mm) => {
                      year match {
                        case Some(yyyy) =>
                          // specific month and year, all hours, all days
                          result += (p -> buildJsonBinsHour(sensor, year, year, month, month, Some(1), Some(31), Some(0), Some(23), p, sources, updateBins))
                        case None =>
                          // specific month, all hours, all days, all years
                          result += (p -> buildJsonBinsHour(sensor, Some(start_year), Some(end_year), month, month, Some(1), Some(31), Some(0), Some(23), p, sources, updateBins))
                      }
                    }
                    case None => {
                      year match {
                        case Some(yyyy) =>
                          // specific year, all hours, all days, all months
                          result += (p -> buildJsonBinsHour(sensor, year, year, Some(1), Some(12), Some(1), Some(31), Some(0), Some(23), p, sources, updateBins))
                        case None =>
                          // all hours, all days, all months, all years
                          result += (p -> buildJsonBinsHour(sensor, Some(start_year), Some(end_year), Some(1), Some(12), Some(1), Some(31), Some(0), Some(23), p, sources, updateBins))
                      }
                    }
                  }
                }
              }
            }
          }
        }
        Ok(Json.obj(
          "name" -> sensor.name,
          "id" -> sensor.id,
          "properties" -> result
        ))
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  def getCachedBinStatsSeason(sensor_id: Int, updateBins: Boolean, parameter: Option[String]) = Action {
    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {

        val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
        val (start_year, end_year, _, _, _, _, _, _) = parseTimeRange(sensor.min_start_time, sensor.max_end_time)
        var result = Json.obj()

        for (p <- sensorObjectParameters) {
          val sources = sensorDB.getSensorSources(sensor_id, p)
          var param = ListBuffer[JsValue]()

          for (current_year <- start_year to end_year) {

            // WINTER = 12/21 - 03/20 ---------
            val winter = buildJsonBinsSeason(sensor_id, current_year, 1, 2, None, 3, Some(12), p, "winter", sources)

            // SPRING = 03/21 - 06/20 ---------
            val spring = buildJsonBinsSeason(sensor_id, current_year, 4, 5, Some(3), 6, None, p, "spring", sources)

            // SUMMER = 06/21 - 09/20 ---------
            val summer = buildJsonBinsSeason(sensor_id, current_year, 7, 8, Some(6), 9, None, p, "summer", sources)

            // FALL = 09/21 - 12/20 ---------
            val fall = buildJsonBinsSeason(sensor_id, current_year, 10, 11, Some(9), 12, None, p, "fall", sources)

            param += winter
            param += spring
            param += summer
            param += fall
          }

          result += (p -> Json.toJson(param))
        }
        Ok(Json.obj("sensor_name" -> sensor.name, "properties" -> result))
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  // Build nested JSON structures for nice results
  private def buildJsonBinsMonth(sensor: SensorModel, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int], parameter: String, sources: List[String], updateBins: Boolean): JsValue = {
    if (updateBins)
      cacheDB.createOrUpdateBinStatsByMonth(sensor.id, start_year, end_year, start_month, end_month, parameter)

    val stats_list = cacheDB.getCachedBinStatsByMonth(sensor.id, start_year, end_year, start_month, end_month, parameter, false)

    var year_set = Json.obj()
    stats_list.foreach(s => {
      val (year, month, count, sum, avg, start_time, end_time) = s
      var month_label = start_time.toLocalDateTime.getMonth.toString.toLowerCase
      month_label = month_label(0).toUpper + month_label.substring(1)
      val label = month_label + " " + year.toString

      var month_set = (year_set \ year.toString).getOrElse(Json.obj()).asInstanceOf[JsObject]

      month_set += (month_label -> Json.obj("count" -> count, "sum" -> sum, "avg" -> avg,
        "start" -> start_time.toLocalDateTime, "end" -> end_time.toLocalDateTime,
        "label" -> label, "sources" -> sources))

      year_set += (year.toString -> month_set.asInstanceOf[JsValue])
    })

    year_set
  }

  private def buildJsonBinsDay(sensor: SensorModel, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int], parameter: String, sources: List[String], updateBins: Boolean): JsValue = {
    if (updateBins)
      cacheDB.createOrUpdateBinStatsByDay(sensor.id, start_year, end_year, start_month, end_month, start_day, end_day, parameter)

    val stats_list = cacheDB.getCachedBinStatsByDay(sensor.id, start_year, end_year, start_month, end_month, start_day, end_day, parameter, false)

    var year_set = Json.obj()
    stats_list.foreach(s => {
      val (year, month, day, count, sum, avg, start_time, end_time) = s
      var month_label = start_time.toLocalDateTime.getMonth.toString.toLowerCase
      month_label = month_label(0).toUpper + month_label.substring(1)
      val month_digit = "%02d".format(start_time.toLocalDateTime.getMonthValue)
      val day_digit = "%02d".format(start_time.toLocalDateTime.getDayOfMonth)
      val label = year.toString + "-" + month_digit + "-" + day_digit

      var month_set = (year_set \ year.toString).getOrElse(Json.obj()).asInstanceOf[JsObject]
      var day_set = (month_set \ month_label).getOrElse(Json.obj()).asInstanceOf[JsObject]

      if (sources.size > 0)
        day_set += (day_digit -> Json.obj("count" -> count, "sum" -> sum, "avg" -> avg,
          "start" -> start_time.toLocalDateTime, "end" -> end_time.toLocalDateTime,
          "label" -> label, "sources" -> sources))
      else
        day_set += (day_digit -> Json.obj("count" -> count, "sum" -> sum, "avg" -> avg,
          "start" -> start_time.toLocalDateTime, "end" -> end_time.toLocalDateTime,
          "label" -> label))

      month_set += (month_label -> day_set)
      year_set += (year.toString -> month_set.asInstanceOf[JsValue])
    })

    year_set
  }

  private def buildJsonBinsHour(sensor: SensorModel, start_year: Option[Int], end_year: Option[Int], start_month: Option[Int], end_month: Option[Int],
    start_day: Option[Int], end_day: Option[Int], start_hour: Option[Int], end_hour: Option[Int], parameter: String, sources: List[String], updateBins: Boolean): JsValue = {
    if (updateBins)
      cacheDB.createOrUpdateBinStatsByMonth(sensor.id, start_year, end_year, start_month, end_month, parameter)

    val stats_list = cacheDB.getCachedBinStatsByHour(sensor.id, start_year, end_year, start_month, end_month,
      start_day, end_day, start_hour, end_hour, parameter, false)

    var year_set = Json.obj()
    stats_list.foreach(s => {
      val (year, month, day, hour, count, sum, avg, start_time, end_time) = s
      var month_label = start_time.toLocalDateTime.getMonth.toString.toLowerCase
      month_label = month_label(0).toUpper + month_label.substring(1)
      val month_digit = "%02d".format(start_time.toLocalDateTime.getMonthValue)
      val day_digit = "%02d".format(start_time.toLocalDateTime.getDayOfMonth)
      val hour_digit = start_time.toLocalDateTime.getHour.toString
      val label = year.toString + "-" + month_digit + "-" + day_digit + " Hour " + hour_digit

      var month_set = (year_set \ year.toString).getOrElse(Json.obj()).asInstanceOf[JsObject]
      var day_set = (month_set \ month_label).getOrElse(Json.obj()).asInstanceOf[JsObject]
      var hour_set = (day_set \ day_digit).getOrElse(Json.obj()).asInstanceOf[JsObject]

      hour_set += (hour_digit -> Json.obj("count" -> count, "sum" -> sum, "avg" -> avg,
        "start" -> start_time.toLocalDateTime, "end" -> end_time.toLocalDateTime,
        "label" -> label, "sources" -> sources))

      day_set += (day_digit -> hour_set)
      month_set += (month_label -> day_set)
      year_set += (year.toString -> month_set.asInstanceOf[JsValue])
    })

    year_set
  }

  private def buildJsonBinsSeason(sensor_id: Int, year: Int, full_month_start: Int, full_month_end: Int, partial_month_start: Option[Int], partial_month_end: Int, historical_month: Option[Int],
    parameter: String, season: String, sources: List[String]): JsValue = {
    /*
     * full_month_start: the first month that we want all of, to get from month bins
     * full_month_end: the last month we want all of, to get from month bins
     * partial_month_start: if season is mid-year, what month to get the 21st - end of month from
     * partial_month_end: what month to get the 1st - 20th from
     * historical_month: what month to get the 21st- end of month from previous year (winter uses this)
     */
    var season_count = 0
    var season_sum = 0.0

    // Full month range from month bins
    val monthbins = cacheDB.getCachedBinStatsByMonth(sensor_id, Some(year), Some(year), Some(full_month_start), Some(full_month_end), parameter, true)
    monthbins.foreach(s => {
      val (year, month, count, sum, avg, start_time, end_time) = s
      season_count += count
      season_sum += sum
    })

    // Partial month from day bins (start and/or end)
    partial_month_start match {
      case Some(pm) => {
        val pmbin = cacheDB.getCachedBinStatsByDay(sensor_id, Some(year), Some(year), partial_month_start, partial_month_start, Some(21), Some(31), parameter, true)
        pmbin.foreach(s => {
          val (year, month, day, count, sum, avg, start_time, end_time) = s
          season_count += count
          season_sum += sum
        })
      }
      case None => {}
    }

    val pmebin = cacheDB.getCachedBinStatsByDay(sensor_id, Some(year), Some(year), Some(partial_month_end), Some(partial_month_end), Some(1), Some(20), parameter, true)
    pmebin.foreach(s => {
      val (year, month, day, count, sum, avg, start_time, end_time) = s
      season_count += count
      season_sum += sum
    })

    // Historical month from previous year if applicable (for Winter)
    historical_month match {
      case Some(hm) => {
        val hmbin = cacheDB.getCachedBinStatsByDay(sensor_id, Some(year - 1), Some(year - 1), Some(hm), Some(hm), Some(21), Some(31), parameter, true)
        hmbin.foreach(s => {
          val (year, month, day, count, sum, avg, start_time, end_time) = s
          season_count += count
          season_sum += sum
        })
      }
      case None => {}
    }

    val season_avg = if (season_count > 0) season_sum / season_count else 0
    return Json.obj(
      // "depth"
      "label" -> (year.toString + " " + season),
      "sources" -> sources,
      "year" -> year,
      // "date"
      // "depth_code"
      "average" -> season_avg,
      "count" -> season_count
    //"sum" -> season_sum
    )
  }

  // Get trends for a parameter
  def getTrendsForParameterYear(sensor_id: Int, parameter: Option[String]) = Action {
    var result = Json.obj()

    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {
        val (start_year, end_year, _, _, _, _, _, _) = parseTimeRange(sensor.min_start_time, sensor.max_end_time)

        parameter match {
          case Some(p) => {
            var subresult = Json.obj()
            val subtotals = cacheDB.getCachedBinStatsByYear(sensor_id, Some(start_year), Some(end_year), p, false)
            subtotals.foreach(s => {
              val (current_year, curr_count, curr_sum, curr_avg, curr_start_time, curr_end_time) = s
              val running_totals = cacheDB.getCachedBinStatsByYear(sensor_id, Some(start_year), Some(current_year), p, true)
              running_totals.foreach(r => {
                // Running totals returns a list with a single entry
                val (run_yr, run_count, run_sum, run_avg, run_start_time, run_end_time) = r
                if (run_avg > 0.0)
                  subresult += (current_year.toString -> Json.toJson((curr_avg - run_avg) / run_avg))
              })
            })
            result += (p -> subresult)
          }
          case None => {
            val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
            sensorObjectParameters.foreach(p => {
              var subresult = Json.obj()
              val subtotals = cacheDB.getCachedBinStatsByYear(sensor_id, Some(start_year), Some(end_year), p, false)
              subtotals.foreach(s => {
                val (curr_year, curr_count, curr_sum, curr_avg, curr_start_time, curr_end_time) = s
                val running_totals = cacheDB.getCachedBinStatsByYear(sensor_id, Some(start_year), Some(curr_year), p, true)
                running_totals.foreach(r => {
                  // Running totals returns a list with a single entry
                  val (run_yr, run_count, run_sum, run_avg, run_start_time, run_end_time) = r
                  if (run_avg > 0.0)
                    subresult += (curr_year.toString -> Json.toJson((curr_avg - run_avg) / run_avg))
                })
              })
              result += (p -> subresult)
            })
          }
        }
        Ok(result)
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  def getTrendsForParameterMonth(sensor_id: Int, parameter: Option[String]) = Action {
    var result = Json.obj()

    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {
        val (start_year, end_year, start_month, end_month, _, _, _, _) = parseTimeRange(sensor.min_start_time, sensor.max_end_time)

        parameter match {
          case Some(p) => {
            var year_set = Json.obj()
            // Get list of per-month stats
            val stats = cacheDB.getCachedBinStatsByMonth(sensor_id, Some(start_year), Some(end_year), Some(1), Some(12), p, false)
            stats.foreach(s => {
              val (curr_year, curr_month, curr_count, curr_sum, curr_avg, curr_start_time, curr_end_time) = s
              // For each month, get sum of all month bins up to this point
              val running_totals = cacheDB.getCachedBinStatsByMonth(sensor_id, Some(start_year), Some(curr_year), Some(1), Some(12), p, true)
              running_totals.foreach(r => {
                // Running totals returns a list with a single entry
                val (run_yr, run_month, run_count, run_sum, run_avg, run_start_time, run_end_time) = r
                if (run_avg > 0.0 && !(run_yr == start_year && run_month < start_month) && !(run_yr == end_year && run_month > end_month)) {
                  val month_label = new DateFormatSymbols().getMonths()(curr_month - 1)
                  var month_set = (year_set \ curr_year.toString).getOrElse(Json.obj()).asInstanceOf[JsObject]
                  month_set += (month_label -> Json.toJson((curr_avg - run_avg) / run_avg))
                  year_set += (curr_year.toString -> month_set.asInstanceOf[JsValue])
                }
              })
            })
            result += (p -> year_set)
          }
          case None => {
            val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
            var years = Json.obj()
            sensorObjectParameters.foreach(p => {
              var subresult = Json.obj()
              // Get list of per-month stats
              val stats = cacheDB.getCachedBinStatsByMonth(sensor_id, Some(start_year), Some(end_year), Some(1), Some(12), p, false)
              stats.foreach(s => {
                val (curr_year, curr_month, curr_count, curr_sum, curr_avg, curr_start_time, curr_end_time) = s
                // For each month, get sum of all month bins up to this point
                val running_totals = cacheDB.getCachedBinStatsByMonth(sensor_id, Some(start_year), Some(curr_year), Some(1), Some(12), p, true)
                running_totals.foreach(r => {
                  // Running totals returns a list with a single entry
                  val (run_yr, run_month, run_count, run_sum, run_avg, run_start_time, run_end_time) = r
                  if (run_avg > 0.0 && !(run_yr == start_year && run_month < start_month) && !(run_yr == end_year && run_month > end_month)) {
                    val month_label = new DateFormatSymbols().getMonths()(curr_month - 1)
                    var month_set = (subresult \ curr_year.toString).getOrElse(Json.obj()).asInstanceOf[JsObject]
                    month_set += (month_label -> Json.toJson((curr_avg - run_avg) / run_avg))
                    subresult += (curr_year.toString -> month_set.asInstanceOf[JsValue])
                  }
                })
              })
              result += (p -> subresult)
            })
          }
        }
        Ok(result)
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  // Shared code for updating a sensor
  private def updateSensorStats(sensor: SensorModel, year: Option[Int], month: Option[Int], day: Option[Int],
    hour: Option[Int], parameter: Option[String], restrict: Boolean) = {
    val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
    val (start_year, end_year, _, _, _, _, _, _) = parseTimeRange(sensor.min_start_time, sensor.max_end_time)

    for (p <- sensorObjectParameters) {
      hour match {
        case Some(hh) => {
          day match {
            case Some(dd) => {
              month match {
                case Some(mm) => {
                  year match {
                    case Some(yyyy) => {
                      // specific hour and day and month and year
                      cacheDB.createOrUpdateBinStatsByHour(sensor.id, year, year, month, month, day, day, hour, hour, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByDay(sensor.id, year, year, month, month, day, day, p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, year, year, month, month, p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, year, year, p)
                      }
                    }
                    case None => {
                      // specific hour and day and month, all years
                      cacheDB.createOrUpdateBinStatsByHour(sensor.id, Some(start_year), Some(end_year), month, month, day, day, hour, hour, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByDay(sensor.id, year, year, month, month, day, day, p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, year, year, month, month, p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, year, year, p)
                      }
                    }
                  }
                }
                case None => {
                  year match {
                    case Some(yyyy) => {
                      // specific hour and day and year, all months
                      cacheDB.createOrUpdateBinStatsByHour(sensor.id, year, year, Some(1), Some(12), day, day, hour, hour, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByDay(sensor.id, year, year, Some(1), Some(12), day, day, p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, year, year, Some(1), Some(12), p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, year, year, p)
                      }
                    }
                    case None => {
                      // specific hour and day, all months, all years
                      cacheDB.createOrUpdateBinStatsByHour(sensor.id, Some(start_year), Some(end_year), Some(1), Some(12), day, day, hour, hour, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByDay(sensor.id, Some(start_year), Some(end_year), Some(1), Some(12), day, day, p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, Some(start_year), Some(end_year), Some(1), Some(12), p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, Some(start_year), Some(end_year), p)
                      }
                    }
                  }
                }
              }
            }
            case None => {
              month match {
                case Some(mm) => {
                  year match {
                    case Some(yyyy) => {
                      // specific hour and month and year, all days
                      cacheDB.createOrUpdateBinStatsByHour(sensor.id, year, year, month, month, Some(1), Some(31), hour, hour, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByDay(sensor.id, year, year, month, month, Some(1), Some(31), p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, year, year, month, month, p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, year, year, p)
                      }
                    }
                    case None => {
                      // specific hour and month, all days, all years
                      cacheDB.createOrUpdateBinStatsByHour(sensor.id, Some(start_year), Some(end_year), month, month, Some(1), Some(31), hour, hour, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByDay(sensor.id, Some(start_year), Some(end_year), month, month, Some(1), Some(31), p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, Some(start_year), Some(end_year), month, month, p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, Some(start_year), Some(end_year), p)
                      }
                    }
                  }
                }
                case None => {
                  year match {
                    case Some(yyyy) => {
                      // specific hour and year, all days, all months
                      cacheDB.createOrUpdateBinStatsByHour(sensor.id, year, year, Some(1), Some(12), Some(1), Some(31), hour, hour, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByDay(sensor.id, year, year, Some(1), Some(12), Some(1), Some(31), p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, year, year, Some(1), Some(12), p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, year, year, p)
                      }
                    }
                    case None => {
                      // specific hour, all days, all months, all years
                      cacheDB.createOrUpdateBinStatsByHour(sensor.id, Some(start_year), Some(end_year), Some(1), Some(12), Some(1), Some(31), hour, hour, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByDay(sensor.id, Some(start_year), Some(end_year), Some(1), Some(12), Some(1), Some(31), p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, Some(start_year), Some(end_year), Some(1), Some(12), p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, Some(start_year), Some(end_year), p)
                      }
                    }
                  }
                }
              }
            }
          }
        }
        case None => {
          day match {
            case Some(dd) => {
              month match {
                case Some(mm) => {
                  year match {
                    case Some(yyyy) => {
                      // specific day and month and year, all hours
                      cacheDB.createOrUpdateBinStatsByDay(sensor.id, year, year, month, month, day, day, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByHour(sensor.id, year, year, month, month, day, day, Some(0), Some(23), p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, year, year, month, month, p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, year, year, p)
                      }
                    }
                    case None => {
                      // specific day and month, all hours, all years
                      cacheDB.createOrUpdateBinStatsByDay(sensor.id, Some(start_year), Some(end_year), month, month, day, day, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByHour(sensor.id, Some(start_year), Some(end_year), month, month, day, day, Some(0), Some(23), p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, Some(start_year), Some(end_year), month, month, p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, Some(start_year), Some(end_year), p)
                      }
                    }
                  }
                }
                case None => {
                  year match {
                    case Some(yyyy) => {
                      // specific day and year, all hours, all months
                      cacheDB.createOrUpdateBinStatsByDay(sensor.id, year, year, Some(1), Some(12), day, day, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByHour(sensor.id, year, year, Some(1), Some(12), day, day, Some(0), Some(23), p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, year, year, Some(1), Some(12), p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, year, year, p)
                      }
                    }
                    case None => {
                      // specific day, all hours, all months, all years
                      cacheDB.createOrUpdateBinStatsByDay(sensor.id, Some(start_year), Some(end_year), Some(1), Some(12), day, day, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByHour(sensor.id, Some(start_year), Some(end_year), Some(1), Some(12), day, day, Some(0), Some(23), p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, Some(start_year), Some(end_year), Some(1), Some(12), p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, Some(start_year), Some(end_year), p)
                      }
                    }
                  }
                }
              }
            }
            case None => {
              month match {
                case Some(mm) => {
                  year match {
                    case Some(yyyy) => {
                      // specific month and year, all hours, all days
                      cacheDB.createOrUpdateBinStatsByMonth(sensor.id, year, year, month, month, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByHour(sensor.id, year, year, month, month, Some(1), Some(31), Some(0), Some(23), p)
                        cacheDB.createOrUpdateBinStatsByDay(sensor.id, year, year, month, month, Some(1), Some(31), p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, year, year, p)
                      }
                    }
                    case None => {
                      // specific month, all hours, all days, all years
                      cacheDB.createOrUpdateBinStatsByMonth(sensor.id, Some(start_year), Some(end_year), month, month, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByHour(sensor.id, Some(start_year), Some(end_year), month, month, Some(1), Some(31), Some(0), Some(23), p)
                        cacheDB.createOrUpdateBinStatsByDay(sensor.id, Some(start_year), Some(end_year), month, month, Some(1), Some(31), p)
                        cacheDB.createOrUpdateBinStatsByYear(sensor.id, Some(start_year), Some(end_year), p)
                      }
                    }
                  }
                }
                case None => {
                  year match {
                    case Some(yyyy) => {
                      // specific year, all hours, all days, all months
                      cacheDB.createOrUpdateBinStatsByYear(sensor.id, year, year, p)
                      if (!restrict) {
                        cacheDB.createOrUpdateBinStatsByHour(sensor.id, year, year, Some(1), Some(12), Some(1), Some(31), Some(0), Some(23), p)
                        cacheDB.createOrUpdateBinStatsByDay(sensor.id, year, year, Some(1), Some(12), Some(1), Some(31), p)
                        cacheDB.createOrUpdateBinStatsByMonth(sensor.id, year, year, Some(1), Some(12), p)
                      }
                    }
                    case None => {
                      // all hours, all days, all months, all years
                      cacheDB.createOrUpdateBinStatsByHour(sensor.id, year, year, Some(1), Some(12), Some(1), Some(31), Some(0), Some(23), p)
                      cacheDB.createOrUpdateBinStatsByDay(sensor.id, year, year, Some(1), Some(12), Some(1), Some(31), p)
                      cacheDB.createOrUpdateBinStatsByMonth(sensor.id, year, year, Some(1), Some(12), p)
                      cacheDB.createOrUpdateBinStatsByYear(sensor.id, year, year, p)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Filter fields out of parameter list that should be ignored, or optionally select only a single target parameter
  private def filterParameters(params: List[String], target: Option[String]): List[String] = {
    params.filter(param => {
      target match {
        case Some(target_param) => {
          param == target_param
        }
        case None => {
          param != "source" && param != "owner" && param != "procedures"
        }
      }
    })
  }

  // Parse start and end strings into component parts
  // TODO: move to util after merge with GEOD-1087
  private def parseTimeRange(start_time: String, end_time: String): (Int, Int, Int, Int, Int, Int, Int, Int) = {
    val start_dt = new DateTime(start_time)
    val end_dt = new DateTime(end_time)

    (
      start_dt.getYear(),
      end_dt.getYear(),
      start_dt.getMonthOfYear(),
      end_dt.getMonthOfYear(),
      start_dt.getDayOfMonth(),
      end_dt.getDayOfMonth(),
      start_dt.getHourOfDay(),
      end_dt.getHourOfDay()
    )
  }
}
