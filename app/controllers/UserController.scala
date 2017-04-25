package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.i18n._

import play.api.data._
import play.api.data.Forms._

case class UserData(name: String, age: Int)

/**
 * User form controller for Play Scala
 */
class UserController @Inject()(implicit val messagesApi: MessagesApi) extends Controller with I18nSupport {

  val userForm = Form(
    mapping(
      "name" -> text,
      "age" -> number
    )(UserData.apply)(UserData.unapply)
  )

  def userGet = Action { implicit request =>
    Ok(views.html.user.form(userForm))
  }

  def userPost = Action { implicit request =>
    userForm.bindFromRequest.fold(
      formWithErrors => {
        // binding failure, you retrieve the form containing errors:
        BadRequest(views.html.user.form(formWithErrors))
      },
      userData => {
        /* binding success, you get the actual value. */       
        /* flashing uses a short lived cookie */ 
        Redirect(routes.UserController.userGet()).flashing("success" -> ("Successful " + userData.toString))
      }
    )
  }
}
