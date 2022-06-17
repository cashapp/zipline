package app.cash.zipline.database

import com.squareup.sqldelight.db.SqlDriver
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class CacheDatabaseJvmTest {
  private val databaseFactory = DatabaseFactory(Produce.Schema)
  private lateinit var driver: SqlDriver
  private lateinit var database: Produce

  @Before
  fun before() {
    driver = databaseFactory.createDriver()
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
}
