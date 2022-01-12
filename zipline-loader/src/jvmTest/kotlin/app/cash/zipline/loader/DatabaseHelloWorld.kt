package app.cash.zipline.loader

import org.junit.Test

class DatabaseHelloWorld {
  @Test
  fun name() {
    val database = createDatabase(DriverFactory())
    database.cacheQueries.transaction {
      database.cacheQueries.insert(99, "ADRW")
      database.cacheQueries.insert(11, "JW")

      val query = database.cacheQueries.selectAll()
      query.addListener()

      for (hockeyPlayer in database.cacheQueries.selectAll().executeAsList()) {
        println(hockeyPlayer)
      }
    }
  }
}
