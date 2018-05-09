package controllers

import javax.inject.{ Inject, Singleton }
import akka.util.ByteString
import utils.{ BinHelper, JsonConvert, Parsers }
import utils.silhouette._
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.{ AuthenticatedEvent, LoginInfo, Silhouette }
import play.api.mvc.{ Action, Controller }
import play.api.{ Configuration, Logger }
import db.{ Datapoints, Events, Sensors, Users, Streams }
import models.DatapointModel
import models.User
import play.api.data._
import play.api.db.Database
import play.api.i18n._
import play.api.libs.functional.syntax._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Json._

import scala.collection.mutable.ListBuffer
import org.joda.time.{ DateTime, IllegalInstantException }
import org.joda.time.format.{ DateTimeFormat, ISODateTimeFormat }
import play.filters.gzip.Gzip
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Json._
import akka.stream._
import akka.stream.scaladsl._
import com.sun.xml.internal.ws.api.message.Header
import play.api.libs.iteratee.{ Enumeratee, Enumerator }
import utils.DatapointsHelper.timeBins

import scala.concurrent.ExecutionContext.Implicits.global
import views.html.{ auth => viewsAuth }

/**
 * Datapoints contain the actual values together with a location and a time interval.
 */
@Singleton
class DatapointController @Inject() (val silhouette: Silhouette[MyEnv], sensorDB: Sensors, datapointDB: Datapoints, userDB: Users,
  eventsDB: Events, streamDB: Streams, conf: Configuration)(implicit val messagesApi: MessagesApi)
    extends AuthController with I18nSupport {

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
        val id = datapointDB.addDatapoint(datapoint)
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
        val datapintCount = datapointDB.addDatapoints(datapointlist)
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
    datapointDB.getDatapoint(id) match {
      case Some(datapoint) => {
        datapointDB.deleteDatapoint(id)
        Ok(Json.obj("status" -> "OK"))
      }
      case None => NotFound(Json.obj("message" -> "Datapoint not found."))

    }

  }

  def renameParam(oldParam: String, newParam: String, source: Option[String], region: Option[String]) = SecuredAction(WithService("master")) {
    datapointDB.renameParam(oldParam, newParam, source, region)
    Ok(Json.obj("status" -> "OK"))
  }

  def datapointSearch(operator: String, since: Option[String], until: Option[String], geocode: Option[String],
    stream_id: Option[String], sensor_id: Option[String], sources: List[String], attributes: List[String],
    format: String, semi: Option[String], onlyCount: Boolean, purpose: Option[String]) = UserAwareAction { implicit request =>
    //TODO: implement operator
    try {
      val raw = datapointDB.searchDatapoints(since, until, geocode, stream_id, sensor_id, sources, attributes, operator != "")

      val filtered = semi match {
        case Some(season) => raw.filter(p => checkSeason(season, p.\("start_time").as[String]))
        case None => raw
      }

      if (onlyCount) {
        Ok(Json.obj("status" -> "OK", "datapointsLength" -> filtered.length))

      } else {
        request.identity match {
          case Some(user) => {
            if (purpose.isDefined) {
              eventsDB.save(user.asInstanceOf[User].id, request.queryString)
            }

            if (format == "csv") {
              val toByteArray: Enumeratee[String, Array[Byte]] = Enumeratee.map[String] { s => s.getBytes }
              Ok.chunked(JsonConvert.jsonToCSV(filtered) &> toByteArray &> Gzip.gzip())
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
            val queryString: String =
              routes.DatapointController.datapointDownload(since, until, geocode, sources, attributes, format).toString

            Redirect(routes.Auth.signIn(Some(queryString)))
          }
        }
      }
    } catch {
      case e => BadRequest(Json.obj("status" -> "KO", "message" -> e.toString))

    }
  }

  def datapointDownload(since: Option[String], until: Option[String], geocode: Option[String],
    sources: List[String], attributes: List[String], format: String) = UserAwareAction { implicit request =>

    request.identity match {
      case Some(user) => {
        val purpose = eventsDB.getLatestPurpose(user.id.getOrElse(0))
        Ok(views.html.sensor.download(since, until, geocode, sources, attributes, format, purpose))
      }
      case None => {
        val queryString: String =
          routes.DatapointController.datapointDownload(since, until, geocode, sources, attributes, format).toString

        Redirect(routes.Auth.signIn(Some(queryString)))
      }
    }
  }

  def datapointsBin(time: String, depth: Double, keepRaw: Boolean, since: Option[String], until: Option[String],
    geocode: Option[String], stream_id: Option[String], sensor_id: Option[String], sources: List[String],
    attributes: List[String]) = Action {
    // what happened if sensor id is not given??
    // TODO list of special properties

    // TODO - remove depth code,

    val groupBy = List("DEPTH_CODE")
    val addAll = List("source")
    val ignore = groupBy ++ addAll

    sensorDB.getSensor(sensor_id.get.toInt) match {
      case Some(sensorObject) => {
        val raw = datapointDB.searchDatapoints(since, until, geocode, stream_id, sensor_id, sources, attributes, true)

        // list of result
        val properties = collection.mutable.HashMap.empty[String, collection.mutable.HashMap[String, BinHelper]]

        raw.map(sensor => {
          val depthCode = sensor.\("properties").\("DEPTH_CODE") match {
            case x: JsUndefined => "NA"
            case x => Parsers.parseString(x)
          }
          val extras = Json.obj("depth_code" -> depthCode)

          // get source
          val source = sensor.\("properties").\("source") match {
            case x: JsUndefined => ""
            case x => Parsers.parseString(x)
          }

          // get depth
          val coordinates = sensor.\("geometry").\("coordinates").as[JsArray]
          val depthBin = depth * Math.ceil(Parsers.parseDouble(coordinates(2)).getOrElse(0.0) / depth)

          // bin time
          val startTime = Parsers.parseDate(sensor.\("start_time")).getOrElse(DateTime.now)
          val endTime = Parsers.parseDate(sensor.\("end_time")).getOrElse(DateTime.now)
          val times = timeBins(time, startTime, endTime)

          sensor.\("properties").as[JsObject].fieldSet.filter(p => !ignore.contains(p._1)).foreach(f => {
            // add to list of properies
            val prop = Parsers.parseString(f._1)
            val propertyBin = properties.getOrElseUpdate(prop, collection.mutable.HashMap.empty[String, BinHelper])

            // add value to all bins
            times.foreach(t => {
              val key = prop + t._1 + depthBin + depthCode

              // add data object
              val bin = propertyBin.getOrElseUpdate(key, BinHelper(depthBin, t._1, extras, t._2))

              // add source to result
              if (source != "") {
                bin.sources += source
              }

              // add values to array
              Parsers.parseDouble(f._2) match {
                case Some(v) => bin.doubles += v
                case None =>
                  f._2 match {
                    case JsObject(_) => {
                      val s = Parsers.parseString(f._2)
                      if (s != "") {
                        bin.array += s
                      }
                    }

                    case _ => {
                      val s = Parsers.parseString(f._2)
                      if (s != "") {
                        bin.strings += s
                      }
                    }

                  }
              }
            })
          })
        })

        // combine results
        // TODO breaks for depth 0.0
        val result = properties.map { p =>
          val elements = for (bin <- p._2.values if bin.doubles.length > 0 || bin.array.size > 0) yield {
            val base = Json.obj("depth" -> bin.depth, "label" -> bin.label, "sources" -> bin.sources.toList)

            val raw = if (keepRaw) {
              Json.obj("doubles" -> bin.doubles.toList, "strings" -> bin.strings.toList)
            } else {
              Json.obj()
            }

            val dlen = bin.doubles.length
            val average = if (dlen > 0) {
              Json.obj("average" -> toJson(bin.doubles.sum / dlen), "count" -> dlen)
            } else {
              Json.obj("array" -> bin.array.toList, "count" -> bin.array.size)
            }

            // return object combining all pieces
            base ++ bin.timeInfo ++ bin.extras ++ raw ++ average
          }
          // add data back to result, sorted by date.
          (p._1, elements.toList.sortWith((x, y) => x.\("date").toString() < y.\("date").toString()))
        }
        Ok(Json.obj("status" -> "OK", "sensor_name" -> sensorObject.name, "properties" -> Json.toJson(result.toMap)))
      }
      case None => Ok(Json.obj("status" -> "no sensor"))
    }
  }

  def datapointsBinUsingQuery(time: String, depth: Double, keepRaw: Boolean, since: Option[String], until: Option[String],
    geocode: Option[String], stream_id: Option[String], sensor_id: Option[String], sources: List[String],
    attributes: List[String]) = Action {
    // what happened if sensor id is not given??
    // TODO list of special properties

    // TODO - remove depth code,

    val groupBy = List("DEPTH_CODE")
    val addAll = List("source")
    val ignore = groupBy ++ addAll

    sensorDB.getSensor(sensor_id.get.toInt) match {
      case Some(sensorObject) => {

        val streams_of_sensor = sensorDB.getSensorStreams(Integer.parseInt(sensor_id.get))

        // TODO for each stream get the bin
        val bins: ListBuffer[JsValue] = ListBuffer.empty[JsValue]
        streams_of_sensor.map(stream => {
          var current_bin = streamDB.getBinForStream(time,1)
          bins += current_bin
        });

        val raw = datapointDB.searchDatapoints(since, until, geocode, stream_id, sensor_id, sources, attributes, true)

        // val raw_bin = datapointDB.searchDatapointsByBin(since, until, geocode, stream_id, sensor_id, sources, attributes, true, time)

        // list of result
        val properties = collection.mutable.HashMap.empty[String, collection.mutable.HashMap[String, BinHelper]]

        raw.map(sensor => {
          val depthCode = sensor.\("properties").\("DEPTH_CODE") match {
            case x: JsUndefined => "NA"
            case x => Parsers.parseString(x)
          }
          val extras = Json.obj("depth_code" -> depthCode)

          // get source
          val source = sensor.\("properties").\("source") match {
            case x: JsUndefined => ""
            case x => Parsers.parseString(x)
          }

          // get depth
          val coordinates = sensor.\("geometry").\("coordinates").as[JsArray]
          val depthBin = depth * Math.ceil(Parsers.parseDouble(coordinates(2)).getOrElse(0.0) / depth)

          // bin time
          val startTime = Parsers.parseDate(sensor.\("start_time")).getOrElse(DateTime.now)
          val endTime = Parsers.parseDate(sensor.\("end_time")).getOrElse(DateTime.now)
          val times = timeBins(time, startTime, endTime)

          sensor.\("properties").as[JsObject].fieldSet.filter(p => !ignore.contains(p._1)).foreach(f => {
            // add to list of properies
            val prop = Parsers.parseString(f._1)
            val propertyBin = properties.getOrElseUpdate(prop, collection.mutable.HashMap.empty[String, BinHelper])

            // add value to all bins
            times.foreach(t => {
              val key = prop + t._1 + depthBin + depthCode

              // add data object
              val bin = propertyBin.getOrElseUpdate(key, BinHelper(depthBin, t._1, extras, t._2))

              // add source to result
              if (source != "") {
                bin.sources += source
              }

              // add values to array
              Parsers.parseDouble(f._2) match {
                case Some(v) => bin.doubles += v
                case None =>
                  f._2 match {
                    case JsObject(_) => {
                      val s = Parsers.parseString(f._2)
                      if (s != "") {
                        bin.array += s
                      }
                    }

                    case _ => {
                      val s = Parsers.parseString(f._2)
                      if (s != "") {
                        bin.strings += s
                      }
                    }

                  }
              }
            })
          })
        })

        // combine results
        // TODO breaks for depth 0.0
        val result = properties.map { p =>
          val elements = for (bin <- p._2.values if bin.doubles.length > 0 || bin.array.size > 0) yield {
            val base = Json.obj("depth" -> bin.depth, "label" -> bin.label, "sources" -> bin.sources.toList)

            val raw = if (keepRaw) {
              Json.obj("doubles" -> bin.doubles.toList, "strings" -> bin.strings.toList)
            } else {
              Json.obj()
            }

            val dlen = bin.doubles.length
            val average = if (dlen > 0) {
              Json.obj("average" -> toJson(bin.doubles.sum / dlen), "count" -> dlen)
            } else {
              Json.obj("array" -> bin.array.toList, "count" -> bin.array.size)
            }

            // return object combining all pieces
            base ++ bin.timeInfo ++ bin.extras ++ raw ++ average
          }
          // add data back to result, sorted by date.
          (p._1, elements.toList.sortWith((x, y) => x.\("date").toString() < y.\("date").toString()))
        }
        Ok(Json.obj("status" -> "OK", "sensor_name" -> sensorObject.name, "properties" -> Json.toJson(result.toMap)))
      }
      case None => Ok(Json.obj("status" -> "no sensor"))
    }
  }

  def trendsByRegionDetail(attribute: String, geocode: String, season: String) = Action {
    // rawdata has 3 field: data, region, time.
    val rawdata = datapointDB.trendsByRegion(attribute, geocode)
    // for debug
    //print(rawdata.head)
    // group rawdata by date get Map[Some[Datetime], List(data, region, time)]
    val rawdataGroupByTime = rawdata.filter(data => checkSeason(season, (data.\("time")).as[String]))
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

  def trendsByRegion(attribute: String, geocode: String, season: String) = Action {
    // rawdata has 3 field: data, region, time.
    val rawdata = datapointDB.trendsByRegion(attribute, geocode)

    // for debug
    //println(rawdata.head)
    var lastDateString: String = new DateTime().withYear(1800).toString
    val dataWithSeason = rawdata.filter { data =>
      val tmpTime = (data.\("time")).as[String]
      val matchSeason = checkSeason(season, tmpTime)
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

      // create the result Jsobject
      var trendsdata: JsObject = Json.obj(
        "totalaverage" -> dataWholeYear.sum / dataWholeYear.length,
        "tenyearsaverage" -> dataTenYears.sum / dataTenYears.length,
        "lastaverage" -> dataLastYear.sum / dataLastYear.length
      )

      Ok(Json.obj("status" -> "OK", "trends" -> trendsdata))
    } else {
      Ok(Json.obj("status" -> "OK", "trends" -> "no data"))
    }

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
    datapointDB.getDatapoint(id) match {
      case Some(datapoint) => Ok(Json.obj("status" -> "OK", "datapoint" -> datapoint))
      case None => NotFound(Json.obj("message" -> "Datapoint not found."))
    }
  }
}
