package controllers

import javax.inject.{Inject, Singleton}

import db.{Sensors, Streams}
import model.{GeometryModel, SensorModel, StreamModel}

import play.api.mvc._
import play.api.mvc.{Action, Controller}
import play.api.mvc.Results._
import play.api.data._
import play.api.db.Database
import play.api.i18n._
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.functional.syntax._


/**
  * Streams are a way of grouping datapoints together. A stream has to belong to a `Sensor` and and `Datapoint` has to
  * belong to a `Stream`.
  */
@Singleton
class StreamController @Inject()(db: Database, sensors: Sensors, streams: Streams) extends Controller {

  /**
    * Create stream.
    *
    * @return id
    */
  def createStream = Action(BodyParsers.parse.json) { implicit request =>
    val streamResult = request.body.validate[StreamModel]
    streamResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      stream => {
        val id = streams.createStream(stream)
        Ok(Json.obj("status" -> "ok", "id" -> id))
      }
    )
  }

  /**
    * Update "min_start_time", "max_end_time", "params" element of sensor and stream.
    * Duplicate with Sensor.updateStatisticsStreamSensor
    *
    */
  def updateStatisticsStreamSensor() = Action {
    sensors.updateSensorStats(None)
    Ok(Json.obj("status" -> "update"))
  }

  /**
    * Retrieve stream. Set `min_start_time` and `max_start_time` based on streams belonging to this stream.
    * TODO: simplify query by setting `min_start_time` and `max_start_time` when updating statistics on sensors and streams.
    *
    * @param id
    * @return
    */
  def getStream(id: Int) = Action {
    val stream = streams.getStream(id)
    streams.getStream(id) match {
      case Some(stream) =>  Ok(Json.obj("status" -> "ok", "stream" -> stream))
      case None => NotFound(Json.obj("message" -> "Stream not found."))
    }

  }

  /**
    * Update `properties` element of stream.
    *
    * @param id
    * @return new stream
    */
  def patchStreamMetadata(id: Int) = Action(parse.json) { implicit request =>
    request.body.validate[(JsValue)].map {
      case (data) => {
        val stream = streams.patchStreamMetadata(id, Json.stringify(data))
        Ok(Json.obj("status" -> "update", "stream" -> stream))
        //TODO: return null stream id is not found
        // match {
//              case Some(d) => Ok(Json.obj("status" -> d))
//              case None => BadRequest(Json.obj("status" ->"Failed to update stream"))
//            }
        }
    }.recoverTotal {
      e => BadRequest("Detected error:" + JsError.toFlatJson(e))
    }
  }

  /**
    * Update "min_start_time", "max_end_time", "params" element of stream.
    *
    */
  def updateStatisticsStream(id: Int) = Action {
    streams.updateStreamStats(Some(id))
    Ok(Json.obj("status" -> "update"))
  }

  /**
    * Search stream.
    *
    * @param geocode, stream_name
    * @return stream
    */
  def searchStreams(geocode: Option[String], stream_name: Option[String]) = Action {
    val searchStreams = streams.searchStreams(geocode, stream_name)
    Ok(Json.obj("status" -> "ok", "streams" -> searchStreams))
  }

  /**
    * Delete stream.
    *
    * @param id
    */
  def deleteStream(id: Int) = Action {
    streams.deleteStream(id)
    Ok(Json.obj("status" -> "ok"))
  }
}
