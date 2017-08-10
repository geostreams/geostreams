package controllers

import java.sql.Statement
import javax.inject._

import db.Sensors
import model.{GeometryModel, SensorModel}
import play.api.data.Forms._
import play.api.data._
import play.api.db.Database
import play.api.i18n._
import play.api.libs.functional.syntax._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Json._

case class SensorData(name: String, age: Int)

/**
  * Sensors or locations are the topmost data structure in the data model. Sensors contain streams and streams contain
  * datapoints.
  */
class SensorController @Inject()(db: Database, sensors: Sensors)(implicit val messagesApi: MessagesApi)
  extends Controller with I18nSupport {

  val sensorForm = Form(
    mapping(
      "name" -> text,
      "age" -> number
    )(SensorData.apply)(SensorData.unapply)
  )

  def sensorFormGet = Action { implicit request =>
    Ok(views.html.sensor.form(sensorForm))
  }

  def sensorFormPost = Action { implicit request =>
    sensorForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.sensor.form(formWithErrors))
      },
      sensorData => {
        /* binding success, you get the actual value. */
        /* flashing uses a short lived cookie */
        Redirect(routes.SensorController.sensorFormGet()).flashing("success" -> ("Successful " + sensorData.toString))
      }
    )
  }

  /**
    * Create sensor.
    *
    * @return
    */
  def sensorCreate = Action(BodyParsers.parse.json) { implicit request =>
    val sensorResult = request.body.validate[SensorModel]
    sensorResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      sensor => {
        val id = sensors.createSensor(sensor)
        Ok(Json.obj("status" -> "OK", "id" -> id))
      }
    )
  }

  /**
    * Retrieve sensor. Set `min_start_time` and `max_start_time` based on streams belonging to this sensor.
    * TODO: simplify query by setting `min_start_time` and `max_start_time` when updating statistics on sensors and streams.
    *
    * @param id
    * @return
    */
  def sensorGet(id: Int) = Action {
    sensors.getSensor(id) match {
      case Some(sensor) => Ok(Json.obj("status" -> "OK", "sensor" -> sensor))
      case None => NotFound(Json.obj("message" -> "Sensor not found."))
    }


  }

  /**
    * Update `properties` element of sensor.
    *
    * @param id
    * @return new sensor definition
    */
  def updateSensorMetadata(id: Int) = Action(parse.json) { implicit request =>
    request.body.validate[(JsObject)].map {
      case (body) =>
        val updatedSensor = sensors.updateSensorMetadata(id, body)
        Ok(Json.obj("status" -> "OK", "sensor" -> updatedSensor))
    }.recoverTotal {
      e => BadRequest("Detected error:" + JsError.toJson(e))
    }
  }

  /**
    * Get `properties` element of sensor.
    *
    * @param id
    * @return sensor definition
    */
  def getSensorStatistics(id: Int) = Action {
    val data = sensors.getSensorStats(id)
    Ok(Json.obj("status" -> "OK",
      "range" -> Map[String, JsValue]("min_start_time" -> (data \ "min_start_time").getOrElse(JsNull),
        "max_end_time" -> (data \ "max_end_time").getOrElse(JsNull)),
      "parameters" -> (data \ "parameters").getOrElse(JsNull)))

  }

  /**
    * Get all streams of a sensor.
    *
    * @param id
    * @return list of streams<id, name>
    */
  def getSensorStreams(id: Int) = Action {
    val streams = sensors.getSensorStreams(id)
    Ok(Json.obj("status" -> "OK", "streams" -> streams))
  }

  /**
    * Update "min_start_time", "max_end_time", "params" element of a senaor.
    *
    * @param id
    * @return
    */
  def updateStatisticsSensor(id: Int) = Action { implicit request =>
    sensors.updateSensorStats(Some(id))
    Ok(Json.obj("status" -> "update"))
  }

  /**
    * Update "min_start_time", "max_end_time", "params" element of all senaors.
    *
    */
  def updateStatisticsStreamSensor() = Action { implicit request =>
    sensors.updateSensorStats(None)
    Ok(Json.obj("status" -> "update"))
  }

  /**
    * Search sensors.
    *
    * @param geocode, sensor_name
    * @return sensor
    */
  def searchSensors(geocode: Option[String], sensor_name: Option[String]) = Action {

    Ok(Json.obj("sensors" ->sensors.searchSensors(geocode, sensor_name)))
  }

  /**
    * Delete sensor.
    *
    * @param id
    */
  def deleteSensor(id: Int) = Action {
    sensors.deleteSensor(id)
    Ok(Json.obj("status" -> "OK"))
  }
}
