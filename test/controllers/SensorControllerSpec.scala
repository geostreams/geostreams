package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._

import scala.io.Source

/**
  * Test sensor controller.
  */
class SensorControllerSpec extends PlaySpec with GuiceOneAppPerSuite {

  // configure application
  override def fakeApplication() = new GuiceApplicationBuilder().configure(Map("db.default.logSql" -> "false")).build()

  // load testing sensor
  val newSensor = Json.parse(Source.fromURL(getClass.getResource("/sensor.json")).getLines.mkString)

  "SensorController" should {
    "create a new sensor" in {

      val request = FakeRequest(POST, "/api/sensors").withHeaders("Host" -> "localhost").withJsonBody(newSensor)
      val createSensor = route(app, request).get

      status(createSensor) mustBe OK
      contentType(createSensor) mustBe Some("application/json")
      val createSensorRes = contentAsJson(createSensor)
      (createSensorRes \ "status").as[String] mustEqual "OK"

      // retrieve sensor
      val sensorId = (createSensorRes \ "id").as[Int]
      val getSensorReq = FakeRequest(GET, "/api/sensors/" + sensorId).withHeaders("Host" -> "localhost")
      val getSensorRes = route(app, getSensorReq).get

      status(getSensorRes) mustBe OK
      contentType(getSensorRes) mustBe Some("application/json")
      val sensor = contentAsJson(getSensorRes)
      (sensor \ "sensor" \ "name").as[String] mustEqual "45023"
    }

    "delete a newly created sensor" in {

      val request = FakeRequest(POST, "/api/sensors").withHeaders("Host" -> "localhost").withJsonBody(newSensor)
      val createSensor = route(app, request).get

      status(createSensor) mustBe OK
      contentType(createSensor) mustBe Some("application/json")
      val createSensorRes = contentAsJson(createSensor)
      (createSensorRes \ "status").as[String] mustEqual "OK"

      // delete sensor
      val sensorId = (createSensorRes \ "id").as[Int]
      val delSensorReq = FakeRequest(DELETE, "/api/sensors/" + sensorId).withHeaders("Host" -> "localhost")
      val delSensorRes = route(app, delSensorReq).get

      status(delSensorRes) mustBe OK
      contentType(delSensorRes) mustBe Some("application/json")
      val delBody = contentAsJson(delSensorRes)
      (delBody \ "status").as[String] mustEqual "OK"
    }
  }
}
