package controllers

import java.sql.Timestamp
import java.text.DateFormatSymbols

import com.mohiva.play.silhouette.api.Silhouette
import db._
import javax.inject.{ Inject, Singleton }
import models.{ RegionModel, SensorModel }
import org.joda.time.DateTime
import play.api.{ Configuration, Logger }
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.{ JsError, JsObject, JsValue, JsArray, Json }
import play.api.mvc.{ Action, Controller }
import utils.Parsers
import utils.DatapointsHelper
import utils.silhouette._

import scala.collection.mutable.ListBuffer

/**
 * Caches are kept to retrieve binned versions of the data (datatapoints) and trends.
 */
@Singleton
class CacheController @Inject() (val silhouette: Silhouette[TokenEnv], sensorDB: Sensors, datapointDB: Datapoints,
  cacheDB: Cache, regionDB: RegionTrends, conf: Configuration)(implicit val messagesApi: MessagesApi)
    extends AuthTokenController with I18nSupport {

  /*
   * Calculate bin values and create or overwrite entries in bin cache tables.
   *  sensor_id -- if not specified, all sensors will be generated
   *  since, until -- SQL query timestamps, e.g. '2017-12', '2013-10-38T12:57:59.923'
   *  parameter -- if not specified, all parameters will be generated
   */
  def calculateBins(sensor_id: Option[Int], since: Option[String], until: Option[String], parameter: Option[String]) = SecuredAction(WithService("master")) {
    sensor_id match {
      case Some(sid) => {
        Logger.debug("Creating or updating all bins for sensor_id " + sid)
        sensorDB.getSensor(sid) match {
          case Some(sensor) => {
            val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
            for (p <- sensorObjectParameters) {
              cacheDB.calculateBinsByHour(sensor.id, since, until, p)
              cacheDB.calculateBinsByDay(sensor.id, since, until, p)
              cacheDB.calculateBinsByMonth(sensor.id, since, until, p)
              cacheDB.calculateBinsByYear(sensor.id, since, until, p)
              cacheDB.calculateBinsBySeason(sensor.id, since, until, p)
            }
            Ok(Json.obj("status" -> "OK"))
          }
          case None => BadRequest(Json.obj("status" -> "sensor not found"))
        }
      }
      case None => {
        val allSensors = sensorDB.searchSensors(None, None)
        Logger.debug("updating " + allSensors.length.toString + " sensors")
        allSensors.foreach(sensor => {
          val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
          for (p <- sensorObjectParameters) {
            cacheDB.calculateBinsByHour(sensor.id, since, until, p)
            cacheDB.calculateBinsByDay(sensor.id, since, until, p)
            cacheDB.calculateBinsByMonth(sensor.id, since, until, p)
            cacheDB.calculateBinsByYear(sensor.id, since, until, p)
            cacheDB.calculateBinsBySeason(sensor.id, since, until, p)
          }
        })

        Ok(Json.obj("status" -> "OK", "sensors" -> allSensors.length.toString))
      }
    }
  }

  def getSeason(yyyy: Int, mm: Int, dd: Int): (Int, String) = {
    var season = ""
    var year = yyyy
    if ((mm == 12 && dd >= 21) || (mm <= 3 && dd <= 20)) {
      season = "winter"
      if(mm == 12) {
        year += 1
      }
    } else if ((mm == 3 && dd >= 21) || mm == 4 || mm == 5 || (mm == 6 && dd <= 20)) {
      season = "spring"
    } else if ((mm == 6 && dd >= 21) || mm == 7 || mm == 8 || (mm == 9 && dd <= 20)) {
      season = "summer"
    } else {
      season = "fall"
    }
    return (year, season)
  }

  /*
     * Try to fetch an existing bin and add a single datapoint to it, creating a new bin if necessary.
     *  sensor_id -- if not specified, all sensors will be generated
     *  since, until -- SQL query timestamps, e.g. '2017-12', '2013-10-38T12:57:59.923'
     *  parameter -- if not specified, all parameters will be generated
     */
  def appendToBins(sensor_id: Int, datapoint_id: Int) = SecuredAction(WithService("master")) {
    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {
        datapointDB.getDatapoint(datapoint_id) match {
          case Some(dp) => {
            val target_time = new DateTime(dp.start_time)
            val yyyy = target_time.getYear()
            val mm = target_time.getMonthOfYear()
            val dd = target_time.getDayOfMonth()
            val hh = target_time.getHourOfDay()
            val (season_year, season) = getSeason(yyyy, mm, dd)

            val sensorObjectParameters = filterParameters(sensor.parameters, None)
            for (p <- sensorObjectParameters) {
              (dp.properties \ p).asOpt[String] match {
                case Some(value) => {
                  try {
                    cacheDB.insertIntoBinYear(sensor, dp, yyyy, p, value.toDouble)
                    cacheDB.insertIntoBinMonth(sensor, dp, yyyy, mm, p, value.toDouble)
                    cacheDB.insertIntoBinDay(sensor, dp, yyyy, mm, dd, p, value.toDouble)
                    cacheDB.insertIntoBinHour(sensor, dp, yyyy, mm, dd, hh, p, value.toDouble)
                    cacheDB.insertIntoBinSeason(sensor, dp, season_year, season, p, value.toDouble)
                  } catch {
                    case e: NumberFormatException => {}
                  }
                }
                case None => {}
              }

            }
            Ok(Json.obj("status" -> "OK"))
          }
          case None => BadRequest(Json.obj("status" -> "datapoint not found"))
        }

      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  /* Get statistics for a sensor from bin cache, optionally recalculating first.
   *  since, until -- SQL query timestamps, e.g. '2017-12', '2013-10-38T12:57:59.923'
   *  updateBins -- true will force the requested bins to be recalculated before returning
   *  parameter -- if not specified, all parameters will be generated
   */
  def getCachedBinStatsYear(sensor_id: Int, since: Option[String], until: Option[String], updateBins: Boolean, parameter: Option[String]) = UserAwareAction { implicit request =>
    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {

        val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
        var result = Json.obj()

        for (p <- sensorObjectParameters) {
          val sources = sensorDB.getSensorSources(sensor_id, p)

          if (updateBins && request.identity.isDefined)
            cacheDB.calculateBinsByYear(sensor.id, since, until, p)

          val stats_list = cacheDB.getCachedBinStatsByYear(sensor, since, until, p, false)

          var result_set = ListBuffer[JsValue]()
          stats_list.foreach(s => {
            val (yyyy, count, sum, avg, start_time, end_time) = s

            result_set += Json.obj(
              "count" -> count,
              // "sum" -> sum,
              "average" -> avg,
              "year" -> yyyy,
              "date" -> start_time.toLocalDateTime,
              "label" -> yyyy.toString,
              "sources" -> sources
            )
          })
          result += (p -> JsArray(result_set.toList))
        }

        Ok(Json.obj(
          "sensor_name" -> sensor.name,
          "properties" -> result
        ))
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  def getCachedBinStatsMonth(sensor_id: Int, since: Option[String], until: Option[String], updateBins: Boolean, parameter: Option[String]) = UserAwareAction { implicit request =>
    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {

        val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
        var result = Json.obj()

        for (p <- sensorObjectParameters) {
          val sources = sensorDB.getSensorSources(sensor_id, p)
          result += (p -> buildJsonBinsMonth(sensor, since, until, p, sources, updateBins))
        }

        Ok(Json.obj(
          "sensor_name" -> sensor.name,
          "properties" -> result
        ))
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  def getCachedBinStatsDay(sensor_id: Int, since: Option[String], until: Option[String], updateBins: Boolean, parameter: Option[String]) = Action {
    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {

        val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
        var result = Json.obj()

        for (p <- sensorObjectParameters) {
          val sources = sensorDB.getSensorSources(sensor_id, p)
          result += (p -> buildJsonBinsDay(sensor, since, until, p, sources, updateBins))
        }

        Ok(Json.obj(
          "sensor_name" -> sensor.name,
          "properties" -> result
        ))
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  def getCachedBinStatsHour(sensor_id: Int, since: Option[String], until: Option[String], updateBins: Boolean, parameter: Option[String]) = Action {
    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {
        val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
        var result = Json.obj()

        for (p <- sensorObjectParameters) {
          val sources = sensorDB.getSensorSources(sensor_id, p)
          result += (p -> buildJsonBinsHour(sensor, since, until, p, sources, updateBins))
        }

        Ok(Json.obj(
          "sensor_name" -> sensor.name,
          "properties" -> result
        ))
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  def getCachedBinStatsSeason(sensor_id: Int, since: Option[String], until: Option[String], updateBins: Boolean, parameter: Option[String], season: Option[String]) = Action {
    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {

        val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
        var result = Json.obj()

        for (p <- sensorObjectParameters) {
          val sources = sensorDB.getSensorSources(sensor_id, p)
          result += (p -> buildJsonBinsSeason(sensor, season, since, until, p, sources, updateBins))

        }
        Ok(Json.obj(
          "sensor_name" -> sensor.name,
          "properties" -> result
        ))
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  /* Get cache data for a single parameter over time and format into a list of JSON bins, optionally recalculating first.
   *  since, until -- SQL query timestamps, e.g. '2017-12', '2013-10-38T12:57:59.923'
   *  sources -- an optional list of URLs for data source, typically fetched using sensorDB.getSensorSources()
   *  updateBins -- true will force the requested bins to be recalculated before returning
   */
  private def buildJsonBinsMonth(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, sources: List[String], updateBins: Boolean): JsValue = {
    if (updateBins)
      cacheDB.calculateBinsByMonth(sensor.id, since, until, parameter)

    val stats_list = cacheDB.getCachedBinStatsByMonth(sensor, since, until, parameter, false)

    var result_set = ListBuffer[JsValue]()
    stats_list.foreach(s => {
      val (year, month, count, sum, avg, start_time, end_time) = s
      var month_label = start_time.toLocalDateTime.getMonth.toString.toLowerCase
      month_label = month_label(0).toUpper + month_label.substring(1)
      val label = month_label + " " + year.toString

      result_set += Json.obj(
        "count" -> count,
        // "sum" -> sum,
        "average" -> avg,
        "year" -> year,
        "month" -> month,
        "date" -> start_time.toLocalDateTime,
        "label" -> label,
        "sources" -> sources
      )
    })

    JsArray(result_set.toList)
  }

  private def buildJsonBinsDay(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, sources: List[String], updateBins: Boolean): JsValue = {
    if (updateBins)
      cacheDB.calculateBinsByDay(sensor.id, since, until, parameter)

    val stats_list = cacheDB.getCachedBinStatsByDay(sensor, since, until, parameter, false)

    var result_set = ListBuffer[JsValue]()
    stats_list.foreach(s => {
      val (year, month, day, count, sum, avg, start_time, end_time) = s
      val month_digit = "%02d".format(start_time.toLocalDateTime.getMonthValue)
      val day_digit = "%02d".format(start_time.toLocalDateTime.getDayOfMonth)
      val label = year.toString + "-" + month_digit + "-" + day_digit

      result_set += Json.obj(
        "count" -> count,
        // "sum" -> sum,
        "average" -> avg,
        "year" -> year,
        "month" -> month,
        "day" -> day,
        "date" -> start_time.toLocalDateTime,
        "label" -> label,
        "sources" -> sources
      )
    })

    JsArray(result_set.toList)
  }

  private def buildJsonBinsHour(sensor: SensorModel, since: Option[String], until: Option[String], parameter: String, sources: List[String], updateBins: Boolean): JsValue = {
    if (updateBins)
      cacheDB.calculateBinsByHour(sensor.id, since, until, parameter)

    val stats_list = cacheDB.getCachedBinStatsByHour(sensor, since, until, parameter, false)

    var result_set = ListBuffer[JsValue]()
    stats_list.foreach(s => {
      val (year, month, day, hour, count, sum, avg, start_time, end_time) = s
      val month_digit = "%02d".format(start_time.toLocalDateTime.getMonthValue)
      val day_digit = "%02d".format(start_time.toLocalDateTime.getDayOfMonth)
      val hour_digit = "%02d".format(start_time.toLocalDateTime.getHour)
      val label = year.toString + "-" + month_digit + "-" + day_digit + " " + hour_digit

      result_set += Json.obj(
        "count" -> count,
        // "sum" -> sum,
        "average" -> avg,
        "year" -> year,
        "month" -> month,
        "day" -> day,
        "hour" -> hour,
        "date" -> start_time.toLocalDateTime,
        "label" -> label,
        "sources" -> sources
      )
    })

    JsArray(result_set.toList)
  }

  private def buildJsonBinsSeason(sensor: SensorModel, season: Option[String], since: Option[String], until: Option[String], parameter: String, sources: List[String], updateBins: Boolean): JsValue = {

    if (updateBins)
      cacheDB.calculateBinsBySeason(sensor.id, since, until, parameter)

    val stats_list = cacheDB.getCachedBinStatsBySeason(sensor, season, since, until, parameter, false)

    var result_set = ListBuffer[JsValue]()
    stats_list.foreach(s => {
      val (year, season, count, sum, avg, start_time, end_time) = s
      val label = year.toString + "-" + season
      result_set += Json.obj(
        "count" -> count,
        // "sum" -> sum,
        "average" -> avg,
        "year" -> year,
        "date" -> since,
        "label" -> (year.toString + " " + season),
        "sources" -> sources
      )
    })
    JsArray(result_set.toList)

  }

  // Get trends for a parameter
  def getTrendsForParameterYear(sensor_id: Int, since: Option[String], until: Option[String], parameter: Option[String]) = Action {
    var result = Json.obj()

    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {
        val (start_year, end_year, start_month, end_month, start_day, end_day, start_hour, end_hour) = (since, until) match {
          case (Some(start_time), Some(end_time)) => DatapointsHelper.parseTimeRange(start_time, end_time)
          case (Some(start_time), None) => DatapointsHelper.parseTimeRange(start_time, sensor.max_end_time)
          case (None, Some(end_time)) => DatapointsHelper.parseTimeRange(sensor.min_start_time, end_time)
          case (None, None) => DatapointsHelper.parseTimeRange(sensor.min_start_time, sensor.max_end_time)
        }

        val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
        sensorObjectParameters.foreach(p => {
          var subresult = Json.obj()
          // Get list of per-year stats
          val subtotals = cacheDB.getCachedBinStatsByYear(sensor, since, until, p, false)
          subtotals.foreach(s => {
            // For each year, get running total up to this point
            val (curr_year, curr_count, curr_sum, curr_avg, curr_start_time, curr_end_time) = s
            val running_totals = cacheDB.getCachedBinStatsByYear(
              sensor,
              Some(start_year.toString + "-01-01"),
              Some(curr_year.toString + "-12-31"), p, true
            )

            running_totals.foreach(r => {
              // Running totals returns a list with a single entry
              val (run_yr, run_count, run_sum, run_avg, run_start_time, run_end_time) = r
              if (run_avg > 0.0)
                subresult += (curr_year.toString -> Json.toJson((curr_avg - run_avg) / run_avg))
            })
          })
          result += (p -> subresult)
        })
        Ok(result)
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  def getTrendsForParameterMonth(sensor_id: Int, since: Option[String], until: Option[String], parameter: Option[String]) = Action {
    var result = Json.obj()

    sensorDB.getSensor(sensor_id) match {
      case Some(sensor) => {
        val (start_year, end_year, start_month, end_month, _, _, _, _) = DatapointsHelper.parseTimeRange(sensor.min_start_time, sensor.max_end_time)

        val sensorObjectParameters = filterParameters(sensor.parameters, parameter)
        var years = Json.obj()
        sensorObjectParameters.foreach(p => {
          var subresult = Json.obj()
          // Get list of per-month stats
          val subtotals = cacheDB.getCachedBinStatsByMonth(sensor, since, until, p, false)
          subtotals.foreach(s => {
            // For each month, get running total up to this point
            val (curr_year, curr_month, curr_count, curr_sum, curr_avg, curr_start_time, curr_end_time) = s
            val running_totals = cacheDB.getCachedBinStatsByMonth(
              sensor,
              Some(start_year.toString + "-01-01"),
              Some(curr_year.toString + "-12-31"), p, true
            )

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
        Ok(result)
      }
      case None => BadRequest(Json.obj("status" -> "sensor not found"))
    }
  }

  // Filter fields out of parameter list that should be ignored, or optionally select only a single target parameter
  private def filterParameters(params: List[String], target: Option[String]): List[String] = {
    val ignored_params = List("source", "owner", "procedures", "comments", "disclaimer")

    params.filter(param => {
      target match {
        case Some(target_param) => param == target_param
        case None => !ignored_params.contains(param)
      }
    })
  }

  /*
    API to ingest region TABLE and region_trends TABLE.
    Require a json file that contains "areas" json like v2 config/area.js,
    and "attributes" as a list of attributes for region trends.
    This API is build specifically for EPA, so it has "spring" and "summer", no other 2 seasons.
    While saving regions(areas), existing ones will be omit( not update).
    "boundary" and "center_coordinate" in region TABLE is not saved now.
    so the region trends is calculated based on the input json's "areas".
  */
  def calculateTrendsByRegion() = SecuredAction(WithService("master"))(parse.json) { implicit request =>
    val regions = request.body.\("areas").validate[List[RegionModel]]
    val attributes = request.body.\("attributes").validate[List[String]]
    regions.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      regions => {

        attributes.fold(
          errors => {
            BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
          },
          attributes => {
            regions.map { region =>
              regionDB.saveRegion(region)
              Logger.debug("Saving region trends for  " + region.properties.\("id").as[String])
              attributes.map { attribute =>
                // rawdata has 3 field: data, region, time.
                val rawdata = regionDB.trendsByRegion(attribute, Json.stringify(region.geometry.coordinates).replace("[", "").replace("]", ""), false)
                // for debug
                // println(rawdata.head)
                List("spring", "summer").foreach { season =>
                  var lastDateString: String = new DateTime().withYear(1800).toString
                  val dataWithSeason = rawdata.filter { data =>
                    val tmpTime = (data.\("time")).as[String]
                    val matchSeason = DatapointsHelper.checkSeason(season, tmpTime)
                    if (matchSeason) {
                      lastDateString = if (lastDateString > tmpTime) lastDateString else tmpTime
                    }
                    matchSeason
                  }
                  if (dataWithSeason.length > 0) {
                    // refine dataWithSeason by convert List(data, time) to List[Double], also remove data as NAN
                    var lastDate = new DateTime(lastDateString)
                    val lastYear = new DateTime(lastDate.getYear(), 1, 1, 1, 1)
                    val tenyearsago = lastYear.minusYears(9)
                    Logger.debug("trendsByRegion last date: " + lastDate)
                    Logger.debug("trendsByRegion last year: " + lastYear)
                    Logger.debug("trendsByRegion last 10 years: " + tenyearsago)

                    val dataWholeYear: List[Double] = dataWithSeason.map(d => Parsers.parseDouble(d.\("data"))).flatten
                    val dataTenYears: List[Double] = dataWithSeason.filter(d => Parsers.parseDate(d.\("time"))
                      .getOrElse(new DateTime()).isAfter(tenyearsago))
                      .map(d => Parsers.parseDouble(d.\("data"))).flatten
                    val dataLastYear: List[Double] = dataWithSeason.filter(d => Parsers.parseDate(d.\("time"))
                      .getOrElse(new DateTime()).isAfter(lastYear))
                      .map(d => Parsers.parseDouble(d.\("data"))).flatten

                    val trendsdata: List[Double] = List(dataLastYear.sum / dataLastYear.length, dataTenYears.sum / dataTenYears.length, dataWholeYear.sum / dataWholeYear.length)
                    regionDB.saveRegionTrends(region, season, attribute, trendsdata)
                  }
                }
              }
            }
            Ok(Json.obj("status" -> "OK"))
          }
        )
      }
    )
  }

  /*
    Get region trends from region_trends TABLE
  */
  def regionTrends(attribute: String, season: String) = Action {
    Ok(Json.obj("status" -> "OK", "trends" -> regionDB.getTrendsByRegion(attribute, season)))
  }

  def getTrendsForParameter(parameter: String, season: Option[String], source: Option[String]) = Action {

    val sensors = source match {
      case Some(the_source) => sensorDB.getSensorsBySource(the_source)
      case None => sensorDB.searchSensors(None, None)
    }

    var sensor_trends = new ListBuffer[JsObject]()
    sensors.map(sensor => {
      val min_start_time = new DateTime(sensor.min_start_time)
      var last_year = min_start_time.getYear()
      val max_end_time = new DateTime(sensor.max_end_time)
      var first_year = max_end_time.getYear()
      var last_average = 0.0
      val allbins = cacheDB.getCachedBinStatsBySeason(sensor, season, None, None, parameter, false)
      var total_count = 0.0
      var total_sum = 0.0
      var last10_count = 0.0
      var last10_sum = 0.0
      allbins.map(bin => {
        val (year, season, count, sum, avg, start_time, end_time) = bin
        total_count += count
        total_sum += sum
        last_year = if (year > last_year) year else last_year
        first_year = if (first_year > year) year else first_year
      })

      val last_10_bins = allbins.filter(d => d._1 > last_year - 9)
      last_10_bins.map(bin => {
        val (year, season, count, sum, avg, start_time, end_time) = bin
        last10_count += count
        last10_sum += sum
        if (year == last_year) {
          last_average = avg
        }
      })
      val avg_10 = if (last10_count > 0) last10_sum / last10_count else Double.NaN
      val total_avg = if (total_count > 0) total_sum / total_count else Double.NaN
      val percentage_change = if (last_year - first_year >= 10) (avg_10 - total_avg) / total_avg * 100 else Double.NaN
      last_average = if (total_count > 0) last_average else Double.NaN

      val sensor_trend = Json.obj(
        "sensor_id" -> sensor.id,
        "sensor" -> sensor,
        "last_time" -> last_year,
        "first_year" -> first_year,
        "diff" -> (last_year - first_year),
        "properties" -> Json.obj(
          "interval_average" -> avg_10.toString(),
          "total_average" -> total_avg.toString(),
          "last_average" -> last_average.toString(),
          "percentage_change" -> percentage_change.toString()
        )
      )
      sensor_trends.append(sensor_trend)
    })
    Ok(Json.obj("status" -> "OK", "trends" -> Json.toJson(sensor_trends)))
  }
}
