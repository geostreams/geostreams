package controllers

import javax.inject.{Inject, Singleton}

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
    datapoints.deleteDatapoint(id)
    Ok(Json.obj("status" -> "OK"))
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

  /**
    * Get datapoint.
    *
    * @param id
    */
  def getDatapoint(id: Int) = Action {
    val datapoint = datapoints.getDatapoint(id)
    Ok(Json.obj("status" -> "OK", "datapoint" -> Json.parse(datapoint)))
  }
}
