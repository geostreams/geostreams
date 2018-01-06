package controllers

import db.{ Users, Events }
import javax.inject._

import play.api._
import play.api.mvc._
import play.api.routing._
import play.api.libs.json._
import play.api.libs.json.Json._
import models._
import utils.silhouette._
import com.mohiva.play.silhouette.api.Silhouette
import play.api.i18n.{ Lang, Messages, MessagesApi }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject() (val silhouette: Silhouette[MyEnv], val messagesApi: MessagesApi,
    val usersDB: Users, val eventsDB: Events) extends AuthController {
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

  def manageUser() = SecuredAction(WithService("master")) { implicit request =>
    val users = usersDB.listAll()
    Ok(views.html.user.manage(users))
  }

  def changeMaster(id: String, enable: Boolean) = SecuredAction(WithService("master")) { implicit request =>
    val user = usersDB.get(id.toInt)
    user match {
      case Some(u) => {
        val oldServices = u.services
        val newUser = if (enable) {
          usersDB.save(u.copy(services = "master" :: oldServices))
        } else {
          usersDB.save(u.copy(services = oldServices.filter(_ != "master")))
        }
        Ok(toJson(Map("status" -> "success")))
      }
      case None => Ok(toJson(Map("status" -> "failed")))
    }
  }

  def listEvents() = SecuredAction(WithService("master")) { implicit request =>
    val events = eventsDB.listAll()
    Ok(views.html.sensor.downloadList(events))
  }

  /**
   *  Javascript routing.
   */
  def javascriptRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.HomeController.changeMaster,
        controllers.routes.javascript.DatapointController.datapointSearch
      )
    ).as("text/javascript")
  }
}
