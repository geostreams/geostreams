package controllers

import com.mohiva.play.silhouette.api.Silhouette
import db._
import javax.inject.{ Inject, Singleton }
import models.RegionModel
import org.joda.time.DateTime
import play.api.{ Configuration, Logger }
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.json.{ JsError, JsObject, Json }
import play.api.mvc.{ Action, Controller }
import utils.Parsers
import utils.DatapointsHelper
import utils.silhouette._

import scala.collection.mutable.ListBuffer

/**
 * Caches are kept to retrieve binned versions of the data (datatapoints) and trends.
 */
@Singleton
class CacheController @Inject() (val silhouette: Silhouette[TokenEnv], datapointDB: Datapoints, regionDB: RegionTrends, conf: Configuration)(implicit val messagesApi: MessagesApi)
    extends AuthTokenController with I18nSupport {

  def cacheInvalidateAction(sensor_id: Option[String] = None, stream_id: Option[String] = None) = Action {
    NotImplemented
  }

  def cacheListAction() = Action {
    NotImplemented
  }

  def cacheFetchAction(filename: String) = Action {
    NotImplemented
  }

  /*
    API to ingest region TABLE and region_trends TABLE.
    Require a json file that contains "areas" json like v2 config/area.js,
    and "attributes" as a list of attributes for region trends.
    This API is build sepfical for EPA, so it has "spring" and "summer", no other 2 seasons.
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
}
