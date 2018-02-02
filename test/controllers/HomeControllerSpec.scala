package controllers

import db.{ Events, Users }
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.test._
import play.api.test.Helpers._
import utils.silhouette.MyEnv
import com.mohiva.play.silhouette.api.{ Env, Environment, EventBus, Silhouette, SilhouetteProvider }
import com.mohiva.play.silhouette.api.actions.{ SecuredAction, UnsecuredAction, UserAwareAction }
import play.api.libs.concurrent.Execution.Implicits._
import com.mohiva.play.silhouette.test._
import models.User

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class HomeControllerSpec extends PlaySpec with GuiceOneAppPerSuite {

  "HomeController GET" should {

    "render the index page from a new instance of controller" in {

      // refer https://www.silhouette.rocks/v5.0/docs/testing
      val identity = User(Some(12), "foo@bar.com", true, "bazqux", "foo", "bar", "NCSA", List("master"))
      implicit val env = FakeEnvironment[MyEnv](Seq(identity.loginInfo -> identity))
      val securedAction = app.injector.instanceOf[SecuredAction]
      val unsecuredAction = app.injector.instanceOf[UnsecuredAction]
      val userAwareAction = app.injector.instanceOf[UserAwareAction]

      val silhouette: Silhouette[MyEnv] = new SilhouetteProvider(env, securedAction, unsecuredAction, userAwareAction)
      val messagesApi = app.injector.instanceOf[MessagesApi]
      val usersDB = app.injector.instanceOf[Users]
      val eventDB = app.injector.instanceOf[Events]
      val controller = new HomeController(silhouette, messagesApi, usersDB, eventDB)
      val home = controller.index().apply(FakeRequest())

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include("Geostreams")

      val manageUser = controller.manageUser().apply(FakeRequest().withAuthenticator[MyEnv](identity.loginInfo))
      status(manageUser) mustBe OK
    }

    "render the index page from the application" in {
      val controller = app.injector.instanceOf[HomeController]
      val home = controller.index().apply(FakeRequest())

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include("Geostreams")
    }

    "render the index page from the router" in {
      // Need to specify Host header to get through AllowedHostsFilter
      val request = FakeRequest(GET, "/").withHeaders("Host" -> "localhost")
      val home = route(app, request).get

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include("Geostreams")
    }
  }
}
