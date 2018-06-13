package controllers

import com.mohiva.play.silhouette.api.actions.{SecuredAction, UnsecuredAction, UserAwareAction}
import com.mohiva.play.silhouette.api.{Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.test._
import db.Sensors
import models.User
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._
import utils.silhouette.TokenEnv

import scala.io.Source

/**
 * Test sensor controller.
 */
class SensorControllerSpec extends PlaySpec with GuiceOneAppPerSuite {
  "SensorController" should {
    val identity = User(Some(12), "foo@bar.com", true, "bazqux", "foo", "bar", "NCSA", List("master"))
    implicit val env = FakeEnvironment[TokenEnv](Seq(identity.loginInfo -> identity))
    val securedAction = app.injector.instanceOf[SecuredAction]
    val unsecuredAction = app.injector.instanceOf[UnsecuredAction]
    val userAwareAction = app.injector.instanceOf[UserAwareAction]

    val silhouette: Silhouette[TokenEnv] = new SilhouetteProvider(env, securedAction, unsecuredAction, userAwareAction)
    implicit val messagesApi = app.injector.instanceOf[MessagesApi]
    val sensorDB = app.injector.instanceOf[Sensors]
    val controller = new SensorController(silhouette, sensorDB)(messagesApi)
    var sensorId: Int = 0
    val newSensor = Json.parse(Source.fromURL(getClass.getResource("/sensor.json")).getLines.mkString)

    "create a new sensor" in {

      //      TODO: use GuiceApplicationBuilder
      //      val fakeApp = new GuiceApplicationBuilder()
      //        .overrides(bind[Environment[TokenEnv]].toInstance(env))
      //        .configure(Helpers.inMemoryDatabase())
      //        .configure(Map("db.default.logSql" -> "false"))
      //        .build()
      //
      //      val Some(createSensor) = route(app, FakeRequest(POST, "/api/sensors")
      //        .withHeaders(CONTENT_TYPE -> JSON)
      //        .withAuthenticator[TokenEnv](identity.loginInfo)
      //        .withBody(newSensor))
      val createSensor = controller.sensorCreate.apply(FakeRequest()
        .withHeaders(CONTENT_TYPE -> JSON)
        .withAuthenticator[TokenEnv](identity.loginInfo)
        .withBody(newSensor))
      status(createSensor) mustEqual OK

      contentType(createSensor) mustBe Some("application/json")
      val createSensorRes = contentAsJson(createSensor)
      (createSensorRes \ "status").as[String] mustEqual "OK"

      // retrieve sensor
      sensorId = (createSensorRes \ "id").as[Int]
      val getSensorReq = controller.sensorGet(sensorId).apply(FakeRequest())

      status(getSensorReq) mustBe OK
      contentType(getSensorReq) mustBe Some("application/json")
      val sensor = contentAsJson(getSensorReq)
      (sensor \ "sensor" \ "name").as[String] mustEqual "Sensor #1"
    }

    "update properties sub-document" in {

      // update properties
      val properties = Json.parse("""{"foo":"bar"}""")

      val updateSensorReq = controller.sensorUpdateMetadata(sensorId).apply(FakeRequest()
        .withAuthenticator[TokenEnv](identity.loginInfo)
        .withBody(properties))

      status(updateSensorReq) mustBe OK
      contentType(updateSensorReq) mustBe Some("application/json")
      val updatedBody = contentAsJson(updateSensorReq)
      (updatedBody \ "sensor" \ "properties" \ "foo").as[String] mustEqual "bar"
    }

    "delete a newly created sensor" in {

      // delete sensor
      val delSensorRequest = controller.sensorDelete(sensorId).apply(FakeRequest()
        .withAuthenticator[TokenEnv](identity.loginInfo))

      status(delSensorRequest) mustBe OK
      contentType(delSensorRequest) mustBe Some("application/json")
      val delBody = contentAsJson(delSensorRequest)
      (delBody \ "status").as[String] mustEqual "OK"

      // Try to get the deleted sensor and verify it doesn't exist
      val getSensorRequest = controller.sensorGet(sensorId).apply(FakeRequest()
        .withAuthenticator[TokenEnv](identity.loginInfo))

      status(getSensorRequest) mustBe NOT_FOUND
      val getBody = contentAsJson(getSensorRequest)
      (getBody \ "message").as[String] mustEqual "Sensor not found."
    }
  }
}
