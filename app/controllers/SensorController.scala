package controllers

import java.sql.Statement
import javax.inject._

import model.{GeometryModel, SensorModel}
import play.api.data.Forms._
import play.api.data._
import play.api.db.Database
import play.api.i18n._
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsValue, _}
import play.api.mvc._

case class SensorData(name: String, age: Int)

/**
  * Sensors or locations are the topmost data structure in the data model. Sensors contain streams and streams contain
  * datapoints.
  */
class SensorController @Inject()(db: Database)(implicit val messagesApi: MessagesApi) extends Controller with I18nSupport {

  implicit val geometryReads: Reads[GeometryModel] = (
    (JsPath \ "type").read[String] and
      (JsPath \ "coordinates").read[List[Double]]
    ) (GeometryModel.apply _)

  implicit val sensorReads: Reads[SensorModel] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "geometry").read[GeometryModel] and
      (JsPath \ 'properties).read[JsValue]
    ) (SensorModel.apply _)

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

  def sensorCreate = Action(BodyParsers.parse.json) { implicit request =>
    val sensorResult = request.body.validate[SensorModel]
    sensorResult.fold(
      errors => {
        BadRequest(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      },
      sensor => {
        // connection will be closed at the end of the block
        db.withConnection { conn =>
          val ps = conn.prepareStatement("INSERT INTO sensors(name, geog, created, metadata) " +
            "VALUES(?, ST_SetSRID(ST_MakePoint(?, ?, ?), 4326), NOW(), CAST(? AS json));",
            Statement.RETURN_GENERATED_KEYS)
          ps.setString(1, sensor.name)
          ps.setDouble(2, sensor.geometry.coordinates(0))
          ps.setDouble(3, sensor.geometry.coordinates(1))
          ps.setDouble(4, sensor.geometry.coordinates(2))
          ps.setString(5, Json.stringify(sensor.properties))
          ps.executeUpdate()
          val rs = ps.getGeneratedKeys
          rs.next()
          val id = rs.getInt(1)
          rs.close()
          ps.close()
          Ok(Json.obj("status" -> "OK", "message" -> ("Sensor '" + id + "' saved.")))
        }
      }
    )
  }

  def sensorGet(id: Int) = Action {
    // connection will be closed at the end of the block
    db.withConnection { conn =>
      // TODO store start time, end time and parameter list in the row and update them when the update sensor endpoint is called.
      // Then simplify this query to not calculate them on the fly.
      val query = "WITH stream_info AS (" +
        "SELECT sensor_id, start_time, end_time, unnest(params) AS param FROM streams WHERE sensor_id=?)" +
        "SELECT row_to_json(t, true) AS my_sensor FROM (" +
        "SELECT gid As id, name, to_char(created AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS created, " +
        "'Feature' As type, metadata As properties, ST_AsGeoJson(1, geog, 15, 0)::json As geometry, " +
        "to_char(min(stream_info.start_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') AS min_start_time, " +
        "to_char(max(stream_info.end_time) AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SSZ') as max_end_time, " +
        "array_agg(distinct stream_info.param) as parameters " +
        "FROM sensors " +
        "LEFT OUTER JOIN stream_info ON stream_info.sensor_id = sensors.gid " +
        "WHERE sensors.gid=?" +
        "GROUP BY gid) AS t"
      val st = conn.prepareStatement(query)
      st.setInt(1, id)
      st.setInt(2, id)
      val rs = st.executeQuery()
      rs.next()
      val data = rs.getString(1)
      rs.close()
      st.close()
      Ok(Json.obj("status" -> "OK", "sensor" -> Json.parse(data)))
    }
  }

  def updateSensorMetadata(id: String) = Action {
    NotImplemented
  }

  def getSensorStatistics(id: String) = Action {
    NotImplemented
  }

  def getSensorStreams(id: String) = Action {
    NotImplemented
  }

  def updateStatisticsSensor(id: String) = Action {
    NotImplemented
  }

  def updateStatisticsStreamSensor() = Action {
    NotImplemented
  }

  def searchSensors(geocode: Option[String], sensor_name: Option[String]) = Action {
    NotImplemented
  }

  def deleteSensor(id: String) = Action {
    NotImplemented
  }
}
