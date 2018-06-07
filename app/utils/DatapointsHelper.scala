package utils

import org.joda.time.DateTime

object DatapointsHelper {
  // check the date is the in the specified season, time format like: 2017-06-15T18:30:50Z
  def checkSeason(season: String, date: String): Boolean = {
    val month = date.slice(5, 7)
    season match {
      case "spring" | "Spring" => month == "03" || month == "04" || month == "05"
      case "summer" | "Summer" => month == "06" || month == "07" || month == "08"
      case _ => false
    }
  }

  def checkSeason(season: String, date: DateTime): Boolean = {
    val month = date.getMonthOfYear()
    season match {
      case "spring" | "Spring" => month == 3 || month == 4 || month == 5
      case "summer" | "Summer" => month == 6 || month == 7 || month == 8
      case _ => false
    }
  }
}