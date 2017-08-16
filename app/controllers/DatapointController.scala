package controllers

import javax.inject.{Inject, Singleton}
import util.{ Parsers, PeekIterator}

import play.api.mvc.{Action, Controller}
import play.api.Logger
import db.Sensors
import db.Datapoints
import model.{GeometryModel, SensorModel, StreamModel, DatapointModel}
import play.api.data._
import play.api.db.Database
import play.api.i18n._
import play.api.libs.functional.syntax._
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
  def addDatapoint(invalidateCache: Boolean) = Action(parse.json) { implicit request =>
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
  def deleteDatapoint(id: Int) = Action {
    datapoints.getDatapoint(id) match {
      case Some(datapoint) => {
        datapoints.deleteDatapoint(id)
        Ok(Json.obj("status" -> "OK"))
      }
      case None => NotFound(Json.obj("message" -> "Datapoint not found."))

    }

  }

  def searchDatapoints(operator: String, since: Option[String], until: Option[String], geocode: Option[String],
    stream_id: Option[String], sensor_id: Option[String], sources: List[String], attributes: List[String],
    format: String, semi: Option[String], onlyCount: Boolean) = Action {
    NotImplemented
  }

  def binDatapoints(time: String, depth: Double, keepRaw: Boolean, since: Option[String], until: Option[String],
    geocode: Option[String], stream_id: Option[String], sensor_id: Option[String], sources: List[String],
    attributes: List[String]) =  Action {
    NotImplemented
  }

  def trendsByRegion(attribute: String) = Action {
    //TODO: change sensor to region
    val wholeyearData = datapoints.searchDatapoints(None, None, None, None, Some("805"), List(attribute), List(), false)
    val peekIter = new PeekIterator(wholeyearData)
    val wholeyearAverage = computeAverage(peekIter)
    println(wholeyearData)
    Ok(Json.obj("status" -> "OK"))
  }

  /**
    * Get datapoint.
    *
    * @param id
    */
  def getDatapoint(id: Int) = Action {
    datapoints.getDatapoint(id) match {
      case Some(datapoint) => Ok(Json.obj("status" -> "OK", "datapoint" -> datapoint))
      case None => NotFound(Json.obj("message" -> "Datapoint not found."))
    }
  }

  /**
    * Compute the average value for a sensor. This will return a single
    * sensor with the average values for that sensor. The next object is
    * waiting in the peekIterator.
    *
    * @param data list of data for all sensors
    * @return a single JsObject which is the average for that sensor
    */
  private def computeAverage(data: PeekIterator[JsObject]): Option[JsObject] = {
    if (!data.hasNext) return None

    val sensor = data.next()
    val counter = collection.mutable.HashMap.empty[String, Int]
    val properties = collection.mutable.HashMap.empty[String, Either[collection.mutable.ListBuffer[String], Double]]
    var startDate = Parsers.parseString(sensor.\("start_time"))
    var endDate = Parsers.parseString(sensor.\("end_time"))
    var streams = collection.mutable.ListBuffer[String](Parsers.parseString(sensor.\("stream_id")))
    sensor.\("properties").as[JsObject].fieldSet.foreach(f => {
      counter(f._1) = 1
      val s = Parsers.parseString(f._2)
      val v = Parsers.parseDouble(s)
      if (v.isDefined) {
        properties(f._1) = Right(v.get)
      } else {
        properties(f._1) = Left(collection.mutable.ListBuffer[String](s))
      }
    })
    val sensorName = sensor.\("sensor_name")

    while (data.hasNext && sensorName.equals(data.peek().get.\("sensor_name"))) {
      val nextSensor = data.next()
      if (startDate.compareTo(Parsers.parseString(nextSensor.\("start_time"))) > 0) {
        startDate = Parsers.parseString(nextSensor.\("start_time"))
      }
      if (endDate.compareTo(Parsers.parseString(nextSensor.\("end_time").toString())) < 0) {
        endDate = Parsers.parseString(nextSensor.\("end_time"))
      }
      if (!streams.contains(Parsers.parseString(nextSensor.\("stream_id")))) {
        streams += Parsers.parseString(nextSensor.\("stream_id"))
      }
      nextSensor.\("properties").as[JsObject].fieldSet.foreach(f => {
        if (properties contains f._1) {
          properties(f._1) match {
            case Left(l) => {
              val s = Parsers.parseString(f._2)
              if (counter(f._1) == 1) {
                val v = Parsers.parseDouble(s)
                if (v.isDefined) {
                  properties(f._1) = Right(v.get)
                }
              } else {
                if (!l.contains(s)) {
                  counter(f._1) = counter(f._1) + 1
                  l += s
                }
              }
            }
            case Right(d) => {
              val v2 = Parsers.parseDouble(f._2)
              if (v2.isDefined) {
                counter(f._1) = counter(f._1) + 1
                properties(f._1) = Right(d + v2.get)
              }
            }
          }
        } else {
          val s = Parsers.parseString(f._2)
          val v = Parsers.parseDouble(s)
          counter(f._1) = 1
          if (v.isDefined) {
            properties(f._1) = Right(v.get)
          } else {
            properties(f._1) = Left(collection.mutable.ListBuffer[String](s))
          }
        }
      })
    }

    // compute average
    val jsProperties = collection.mutable.HashMap.empty[String, JsValue]
    properties.foreach(f => {
      jsProperties(f._1) = properties(f._1) match {
        case Left(l) => {
          if (counter(f._1) == 1) {
            Json.toJson(l.head)
          } else {
            Json.toJson(l.toArray)
          }
        }
        case Right(d) => Json.toJson(d / counter(f._1))
      }
    })

    // update sensor
    Some(sensor ++ Json.obj("properties" -> Json.toJson(jsProperties.toMap),
      "start_time" -> startDate,
      "end_time"   -> endDate,
      "stream_id"  -> Json.toJson(streams)))
  }

}
