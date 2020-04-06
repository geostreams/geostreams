package controllers

import javax.inject.{ Inject, Singleton }

import db.{ Cache, Datapoints, Sensors, Streams }
import play.api.libs.json.Json
import play.api.mvc.{ Action, Controller }

/**
 * Admin level endpoints.
 */
@Singleton
class AdminController @Inject() (cacheDB: Cache, streamsDB: Streams, sensorDB: Sensors, datapointsDB: Datapoints)
    extends Controller {

  def counts(sensor_id: Option[Int]) = Action {
    val count_bins_year = cacheDB.getCountBinsByYear(sensor_id)
    val count_bins_season = cacheDB.getCountBinsBySeason(sensor_id)
    val count_bins_month = cacheDB.getCountBinsByMonth(sensor_id)
    val count_bins_day = cacheDB.getCountBinsByDay(sensor_id)
    val count_bins_hour = cacheDB.getCountBinsByHour(sensor_id)
    val count_streams = streamsDB.getCount(sensor_id)
    val count_sensors = sensorDB.getCount(sensor_id)
    val count_datapoints = datapointsDB.getCount(sensor_id)

    Ok(Json.obj(
      "status" -> "OK",
      "sensors" -> count_sensors,
      "streams" -> count_streams,
      "datapoints" -> count_datapoints,
      "bins_year" -> count_bins_year,
      "bins_season" -> count_bins_season,
      "bins_month" -> count_bins_month,
      "bins_day" -> count_bins_day,
      "bins_hour" -> count_bins_hour
    ))
  }
}
