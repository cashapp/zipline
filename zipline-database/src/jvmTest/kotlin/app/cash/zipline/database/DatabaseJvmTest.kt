package app.cash.zipline.database

import com.squareup.sqldelight.db.SqlDriver
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class DatabaseJvmTest {
  private val driverFactory = DriverFactory(Produce.Schema)
  private lateinit var driver: SqlDriver
  private lateinit var database: Produce

  @Before
  fun before() {
    driver = driverFactory.createDriver()
    database = Produce(driver)
  }

  @After
  fun tearDown() {
    driver.close()
  }

  @Test
  fun `happy path`() {
    assertEquals(0, database.produceQueries.count().executeAsOne())
  }

  @Test
  fun `multiple create calls succeed`() {
    driverFactory.createDriver()
    driverFactory.createDriver()
    driverFactory.createDriver()
  }
}
