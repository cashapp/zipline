/*
 * Copyright (C) 2025 Cash App
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.cash.zipline.loader

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import okio.IOException

/**
 * A SQL Driver that always throws [IOException]. Used this when creating a driver fails, such as
 * when the disk is full.
 */
internal class NullSqlDriver : SqlDriver {
  override fun <R> executeQuery(
    identifier: Int?,
    sql: String,
    mapper: (SqlCursor) -> QueryResult<R>,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?,
  ) = throw IOException("NullSqlDriver")

  override fun execute(
    identifier: Int?,
    sql: String,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?,
  ) = throw IOException("NullSqlDriver")

  override fun newTransaction() = throw IOException("NullSqlDriver")

  override fun currentTransaction() = throw IOException("NullSqlDriver")

  override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

  override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

  override fun notifyListeners(vararg queryKeys: String) = Unit

  override fun close() = Unit
}
