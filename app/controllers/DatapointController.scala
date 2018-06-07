package controllers

import javax.inject.{ Inject, Singleton }
import akka.util.ByteString
import utils.{ DatapointsHelper, JsonConvert, Parsers }
import utils.silhouette._
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.{ AuthenticatedEvent, LoginInfo, Silhouette }
import play.api.mvc.{ Action, Controller }
import play.api.{ Configuration, Logger }
import db._
import models.{ DatapointModel, RegionModel, User }
import play.api.data._
import play.api.db.Database
import javax.inject.{ Inject, Singleton }
import models.DatapointModel
import org.joda.time.DateTime
import play.api.i18n._
import play.api.libs.iteratee.{ Enumeratee, Enumerator }
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.mvc.Action
import play.api.{ Configuration, Logger }
import play.filters.gzip.Gzip
import utils.silhouette._
import utils.{ JsonConvert, Parsers }
import views.html.{ auth => viewsAuth }

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Datapoints contain the actual values together with a location and a time interval.
 */
@Singleton
class DatapointController @Inject() (val silhouette: Silhouette[TokenEnv], datapoints: Datapoints, userDB: Users,
  eventsDB: Events, regionDB: RegionTrends, conf: Configuration)(implicit val messagesApi: MessagesApi)
    extends AuthTokenController with I18nSupport {

  /**
   * add datapoint.
   *
   * @return id
   */
  def datapointCreate(invalidateCache: Boolean) = SecuredAction(WithService("master"))(parse.json) { implicit request =>
    Logger.debug("Adding datapoint: " + request.body)

    val datapointResult = request.body.validate[DatapointModel]

    datapointResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      datapoint => {
        val id = datapoints.addDatapoint(datapoint)
        Ok(Json.obj("status" -> "ok", "id" -> id))
      }
    )
  }

  /**
   * add a list of datapoints.
   *
   * @return count
   */
  def datapointsCreate(invalidateCache: Boolean) = Action(parse.json) { implicit request =>
    val datapointResult = request.body.validate[List[DatapointModel]]

    datapointResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      datapointlist => {
        val datapintCount = datapoints.addDatapoints(datapointlist)
        Ok(Json.obj("status" -> "ok", "count" -> datapintCount))
      }
    )
  }

  /**
   * Delete datapoint.
   *
   * @param id
   */
  def datapointDelete(id: Int) = SecuredAction(WithService("master")) {
    datapoints.getDatapoint(id) match {
      case Some(datapoint) => {
        datapoints.deleteDatapoint(id)
        Ok(Json.obj("status" -> "OK"))
      }
      case None => NotFound(Json.obj("message" -> "Datapoint not found."))

    }
  }

  def renameParam(oldParam: String, newParam: String, source: Option[String], region: Option[String]) = SecuredAction(WithService("master")) {
    datapoints.renameParam(oldParam, newParam, source, region)
    Ok(Json.obj("status" -> "OK"))
  }

  def datapointAverage(since: Option[String], until: Option[String], geocode: Option[String],
    stream_id: Option[String], sensor_id: Option[String], sources: List[String], attributes: List[String],
    format: String, semi: Option[String], onlyCount: Boolean) = Action {
    NotImplemented
  }

  def datapointTrends(since: Option[String], until: Option[String], geocode: Option[String],
    stream_id: Option[String], sensor_id: Option[String], sources: List[String], attributes: List[String],
    format: String, semi: Option[String], onlyCount: Boolean) = Action {
    NotImplemented
  }

  def datapointSearch(since: Option[String], until: Option[String], geocode: Option[String],
    stream_id: Option[String], sensor_id: Option[String], sources: List[String], attributes: List[String],
    format: String, semi: Option[String], onlyCount: Boolean) = UserAwareAction { implicit request =>

    try {
      val raw = datapoints.searchDatapoints(since, until, geocode, stream_id, sensor_id, sources, attributes, false)

      val filtered = semi match {
        case Some(season) => raw.filter(p => DatapointsHelper.checkSeason(season, p.\("start_time").as[String]))
        case None => raw
      }

      if (onlyCount) {
        Ok(Json.obj("status" -> "OK", "datapointsLength" -> filtered.length))

      } else {
        request.identity match {
          case Some(user) => {

            if (format == "csv") {
              val toByteArray: Enumeratee[String, Array[Byte]] = Enumeratee.map[String] { s => s.getBytes }
              val strings = raw.length match {
                case 0 => Enumerator("no data")
                case _ => JsonConvert.jsonToCSV(filtered)
              }
              Ok.chunked(strings &> toByteArray &> Gzip.gzip())
                .withHeaders(
                  ("Content-Disposition", "attachment; filename=datapoints.csv"),
                  ("Content-Encoding", "gzip")
                )
                .as(withCharset("text/csv"))
            } else {
              Ok(JsArray(filtered)).withHeaders("Content-Disposition" -> "attachment; filename=download.json")
            }
          }
          case None => {
            Forbidden(Json.obj("status" -> "KO", "message" -> "please provide a valid token"))
          }
        }
      }
    } catch {
      case e => BadRequest(Json.obj("status" -> "KO", "message" -> e.toString))

    }
  }

  def datapointsBin(time: String, depth: Double, keepRaw: Boolean, since: Option[String], until: Option[String],
    geocode: Option[String], stream_id: Option[String], sensor_id: Option[String], sources: List[String],
    attributes: List[String]) = Action {
    NotImplemented
  }

  /**
   * Get datapoint.
   *
   * @param id
   */
  def datapointGet(id: Int) = Action {
    datapoints.getDatapoint(id) match {
      case Some(datapoint) => Ok(Json.obj("status" -> "OK", "datapoint" -> datapoint))
      case None => NotFound(Json.obj("message" -> "Datapoint not found."))
    }
  }

  def trendsByRegionDetail(attribute: String, geocode: String, season: String) = Action {
    // rawdata has 3 field: data, region, time.
    val rawdata = regionDB.trendsByRegion(attribute, geocode, true)
    // for debug
    //print(rawdata.head)
    // group rawdata by date get Map[Some[Datetime], List(data, region, time)]
    val rawdataGroupByTime = rawdata.filter(data => DatapointsHelper.checkSeason(season, (data.\("time")).as[String]))
      .groupBy(data => Parsers.parseDate(data.\("time")).getOrElse(new DateTime()).getYear())
    // refine rawdataGroupByTime by convert List(data, region, time) to List[Double], also remove data as NAN
    var dataGroupByTime: collection.mutable.Map[Int, List[Double]] = collection.mutable.Map()
    rawdataGroupByTime.foreach(rawdata => dataGroupByTime += rawdata._1 -> rawdata._2.map(d => Parsers.parseDouble(d.\("data"))).flatten)
    // calculate average and deviation,
    var averagedata: ListBuffer[JsObject] = ListBuffer()
    var deviationdata: ListBuffer[JsObject] = ListBuffer()
    dataGroupByTime.foreach { d =>
      val count = d._2.length
      val mean = d._2.sum / count
      val devs = d._2.map(score => (score - mean) * (score - mean))
      val stddev = Math.sqrt(devs.sum / (count - 1))
      val dataObject = Json.obj({ d._1.toString -> mean.toString })
      averagedata += dataObject
      deviationdata += Json.obj({ d._1.toString -> stddev.toString })
    }
    Ok(Json.obj("status" -> "OK", "average" -> averagedata.toList, "deviation" -> deviationdata.toList))
  }

}
