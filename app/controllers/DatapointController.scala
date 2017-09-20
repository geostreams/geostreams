package controllers

import javax.inject.{Inject, Singleton}
import java.security.MessageDigest
import java.io.{File, PrintStream}
import util.{ Parsers, PeekIterator}

import play.api.mvc.{Action, Controller}
import org.joda.time.DateTime
import play.api.mvc.{Request, Result}
import play.api.Logger
import db.Sensors
import db.{ Datapoints, Sensors, Streams}
import model.{GeometryModel, SensorModel, StreamModel, DatapointModel}
import play.api.data._
import play.api.db.Database
import play.api.i18n._
import play.api.libs.functional.syntax._
import play.api.libs.iteratee.{Enumeratee, Enumerator}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import play.api.Play.current
import scala.collection.mutable.ListBuffer
import org.joda.time.{DateTime, IllegalInstantException}
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import play.filters.gzip.Gzip
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Json._

/**
  * Datapoints contain the actual values together with a location and a time interval.
  */
@Singleton
class DatapointController @Inject()(db: Database, datapoints: Datapoints) extends Controller {
  /**
    * add datapoint.
    *
    * @return id
    */
  def datapointCreate(invalidateCache: Boolean) = Action(parse.json) { implicit request =>
    Logger.debug("Adding datapoint: " + request.body)

    val datapointResult = request.body.validate[DatapointModel]

    datapointResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      datapoint => {
        // TODO: add new type of datapoint with geometry is GeoJSON
        // https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS/repos/clowder/browse/app/api/Geostreams.scala?at=refs%2Ftags%2Fv1.3.1#427
        val id = datapoints.addDatapoint(datapoint)
        Ok(Json.obj("status" -> "ok", "id" -> id))
      }
    )

  }

  /**
    * Delete datapoint.
    *
    * @param id
    */
  def datapointDelete(id: Int) = Action {
    datapoints.getDatapoint(id) match {
      case Some(datapoint) => {
        datapoints.deleteDatapoint(id)
        Ok(Json.obj("status" -> "OK"))
      }
      case None => NotFound(Json.obj("message" -> "Datapoint not found."))

    }

  }

  def renameParam(oldParam: String, newParam: String, source: Option[String], region: Option[String]) = Action {
    datapoints.renameParam(oldParam, newParam, source, region)
    Ok(Json.obj("status" -> "OK"))
  }

  def datapointSearch(operator: String, since: Option[String], until: Option[String], geocode: Option[String],
                      stream_id: Option[String], sensor_id: Option[String], sources: List[String], attributes: List[String],
                      format: String, semi: Option[String], onlyCount: Boolean) = Action { implicit request =>
    NotImplemented
  }

  def datapointsBin(time: String, depth: Double, keepRaw: Boolean, since: Option[String], until: Option[String],
                    geocode: Option[String], stream_id: Option[String], sensor_id: Option[String], sources: List[String],
                    attributes: List[String]) =  Action {
    NotImplemented
  }

  def trendsByRegionDetail(attribute: String, geocode: String, season: String) = Action {
    // rawdata has 3 field: data, region, time.
    val rawdata = datapoints.trendsByRegion(attribute, geocode)
    // for debug
    //print(rawdata.head)
    // group rawdata by date get Map[Some[Datetime], List(data, region, time)]
    val rawdataGroupByTime = rawdata.filter(data => checkSeason(season, (data.\("time")).as[String]))
      .groupBy( data => Parsers.parseDate(data.\("time")).getOrElse(new DateTime()).getYear())
    // refine rawdataGroupByTime by convert List(data, region, time) to List[Double], also remove data as NAN
    var dataGroupByTime: collection.mutable.Map[Int, List[Double]] = collection.mutable.Map()
    rawdataGroupByTime.foreach(rawdata =>  dataGroupByTime += rawdata._1 -> rawdata._2.map(d => Parsers.parseDouble(d.\("data"))).flatten)
    // calculate average and deviation,
    var averagedata: ListBuffer[JsObject] = ListBuffer()
    var deviationdata: ListBuffer[JsObject]  = ListBuffer()
    dataGroupByTime.foreach { d =>
      val count = d._2.length
      val mean = d._2.sum / count
      val devs = d._2.map(score => (score - mean) * (score - mean))
      val stddev = Math.sqrt(devs.sum / (count - 1))
      val dataObject = Json.obj({d._1.toString ->  mean.toString})
      averagedata += dataObject
      deviationdata += Json.obj({d._1.toString ->  stddev.toString})
    }
    Ok(Json.obj("status" -> "OK", "average" -> averagedata.toList, "deviation" -> deviationdata.toList))
  }

  def trendsByRegion(attribute: String, geocode: String, season: String) = Action {
    // rawdata has 3 field: data, region, time.
    val rawdata = datapoints.trendsByRegion(attribute, geocode)
    // for debug
    //println(rawdata.head)
    val dataWithSeason = rawdata.filter(data => checkSeason(season, (data.\("time")).as[String]))

    // refine dataWithSeason by convert List(data, time) to List[Double], also remove data as NAN
    var tenyearsago = new DateTime()
    tenyearsago = tenyearsago.minusYears(10)

    val dataWholeYear: List[Double] = dataWithSeason.map(d => Parsers.parseDouble(d.\("data"))).flatten
    val dataTenYears: List[Double] = dataWithSeason.filter(d => Parsers.parseDate(d.\("time")).getOrElse(new DateTime()).isAfter(tenyearsago))
      .map(d => Parsers.parseDouble(d.\("data"))).flatten

    //create the result Jsobject
    // TODO: lastaverage, the lastest data or the average of last year?
    var trendsdata:JsObject = Json.obj("totalaverage" -> dataWholeYear.sum / dataWholeYear.length,
          "tenyearsaverage" -> dataTenYears.sum / dataTenYears.length, "lastaverage" -> 0)

    Ok(Json.obj("status" -> "OK", "trends" -> trendsdata))
  }

  // check the date is the in the specified season
  private def checkSeason(season: String, date: String): Boolean = {
    val month = date.slice(5, 7)
    season match {
      case "spring" | "Spring" => month == "03" || month == "04" || month == "05"
      case "summer" | "Summer" => month == "06" || month == "07" || month == "08"
      case _ => false
     }
  }

  private def checkSeason(season: String, date: DateTime): Boolean = {
    val month = date.getMonthOfYear()
    season match {
      case "spring" | "Spring" => month == 3 || month == 4 || month == 5
      case "summer" | "Summer" => month == 6 || month == 7 || month == 8
      case _ => false
    }
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
}
