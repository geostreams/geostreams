package controllers

import java.io.{ PrintWriter, StringWriter }

import akka.stream.scaladsl.Source
import com.mohiva.play.silhouette.api.Silhouette
import db.{ Datapoints, Events, Users }
import javax.inject._
import models._
import play.api._
import play.api.i18n.MessagesApi
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.mvc._
import play.api.routing._
import play.filters.gzip.Gzip
import utils.JsonConvert
import utils.silhouette._

import scala.concurrent.ExecutionContext.Implicits._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject() (val silhouette: Silhouette[CookieEnv], val messagesApi: MessagesApi,
    val usersDB: Users, val eventsDB: Events, datapointsDB: Datapoints) extends AuthCookieController {
  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */

  def index = UserAwareAction { implicit request =>
    Ok(views.html.index())
  }

  def oldApi(page: String) = UserAwareAction {
    Ok(Json.obj("status" -> "You are using old APIs, please remove 'geostreams' in URL"))
  }

  def manageUser() = SecuredAction(WithCookieService("master")) { implicit request =>
    val users = usersDB.listAll()
    Ok(views.html.user.manage(users))
  }

  def changeMaster(id: String, enable: Boolean) = SecuredAction(WithCookieService("master")) { implicit request =>
    val user = usersDB.get(id.toInt)
    user match {
      case Some(u) => {
        val oldServices = u.services
        val newUser = if (enable) {
          usersDB.save(u.copy(services = ("master" :: oldServices).distinct))
        } else {
          usersDB.save(u.copy(services = oldServices.filter(_ != "master").distinct))
        }
        Ok(toJson(Map("status" -> "success")))
      }
      case None => Ok(toJson(Map("status" -> "failed")))
    }
  }

  def listEvents() = SecuredAction(WithCookieService("master")) { implicit request =>
    val events = eventsDB.listAll()
    Ok(views.html.sensor.downloadList(events))
  }

  def datapointDownload(since: Option[String], until: Option[String], geocode: Option[String],
    sources: List[String], attributes: List[String], sensor_id: Option[String]) = UserAwareAction { implicit request =>

    request.identity match {
      case Some(aUser) => {
        implicit val user = request.identity.get
        val purpose = eventsDB.getLatestPurpose(aUser.id.getOrElse(0))

        Ok(views.html.sensor.download(since, until, geocode, sources, attributes, sensor_id, purpose))
      }
      case None => {
        val queryString: String =
          routes.HomeController.datapointDownload(since, until, geocode, sources, attributes, sensor_id).toString

        Redirect(routes.Auth.signIn(Some(queryString)))
      }
    }
  }

  def datapointDownloadCSV(since: Option[String], until: Option[String], geocode: Option[String],
    sources: List[String], attributes: List[String], sensor_id: Option[String],
    purpose: String) = SecuredAction(WithCookieService("serviceDownload")) { implicit request =>

    try {
      val raw = datapointsDB.searchDatapoints(since, until, geocode, None, sensor_id, sources, attributes, false)
      eventsDB.save(request.identity.asInstanceOf[User].id, request.queryString)
      if (raw.length > 0) {
        val toByteArray: Enumeratee[String, Array[Byte]] = Enumeratee.map[String] { s => s.getBytes }
        Ok.chunked(JsonConvert.jsonToCSV(raw) &> toByteArray &> Gzip.gzip())
          .withHeaders(
            ("Content-Disposition", "attachment; filename=datapoints.csv"),
            ("Content-Encoding", "gzip")
          )
          .as(withCharset("text/csv"))

      } else {
        val source = Source.apply(List("no data"))
        Ok.chunked(source)
          .withHeaders(
            ("Content-Disposition", "attachment; filename=datapoints.csv")
          )
          .as(withCharset("text/csv"))
      }

    } catch {
      case e => {
        val sw = new StringWriter()
        val pw = new PrintWriter(sw)
        e.printStackTrace(pw)
        BadRequest(Json.obj("status" -> "KO", "message" -> sw.toString))
      }

    }
  }

  /**
   *  Javascript routing.
   */
  def javascriptRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.HomeController.changeMaster,
        controllers.routes.javascript.HomeController.datapointDownloadCSV
      )
    ).as("text/javascript")
  }
}
