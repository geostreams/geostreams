package controllers

import db.{ Datapoints, Sensors, Streams }
import javax.inject.Inject
import play.api.libs.json.Json
import play.api.mvc._

/** Status information about the service. */
class StatusController @Inject() (sensors: Sensors, streams: Streams, datapoints: Datapoints) extends Controller {

  def status = Action {
    // TODO add datapoints counts / switch to simple count SQL query for all three
    val numSensors = sensors.searchSensors().size
    val numStreams = streams.searchStreams().size
    Ok(Json.obj("status" -> "ok", "counts" -> Json.obj("sensors" -> numSensors, "streams" -> numStreams)))
  }
}
