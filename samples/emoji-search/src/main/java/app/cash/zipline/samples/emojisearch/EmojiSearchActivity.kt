/*
 * Copyright (C) 2021 Square, Inc.
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
package app.cash.zipline.samples.emojisearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NoLiveLiterals
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

@NoLiveLiterals
class EmojiSearchActivity : ComponentActivity() {
  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val events = MutableSharedFlow<EmojiSearchEvent>(extraBufferCapacity = Int.MAX_VALUE)
    val models = MutableStateFlow(initialViewModel)

    val emojiSearchZipline = EmojiSearchZipline()
    emojiSearchZipline.produceModelsIn(scope, events, models)

    setContent {
      val modelsState = models.collectAsState()
      EmojiSearchTheme {
        EmojiSearch(modelsState.value) { event ->
          events.tryEmit(event)
        }
      }
    }
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }
}

@Composable
fun EmojiSearch(
  model: EmojiSearchViewModel,
  events: (EmojiSearchEvent) -> Unit
) {
  Surface(
    color = MaterialTheme.colors.background,
    modifier = Modifier
      .fillMaxWidth()
      .fillMaxHeight(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
    ) {
      SearchField(model.searchTerm, events)
      SearchResults(model.images)
    }
  }
}

@Composable
fun SearchField(
  searchTerm: String,
  events: (EmojiSearchEvent) -> Unit
) {
  val searchFieldValue = remember {
    mutableStateOf(TextFieldValue(text = searchTerm))
  }

  TextField(
    value = searchFieldValue.value,
    onValueChange = {
      events(EmojiSearchEvent.SearchTermEvent(it.text))
      searchFieldValue.value = it
    },
    maxLines = 2,
    textStyle = Typography.h3,
    modifier = Modifier
      .padding(16.dp)
      .fillMaxWidth(),
  )
}

@Composable
private fun SearchResults(emojiImages: List<EmojiImage>) {
  LazyColumn(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth(),
  ) {
    items(emojiImages) { emojiImage ->
      Image(
        painter = rememberImagePainter(emojiImage.url),
        contentDescription = null,
        modifier = Modifier
          .size(64.dp)
          .padding(8.dp)
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
  val events = fun(_: EmojiSearchEvent) = Unit
  EmojiSearchTheme {
    EmojiSearch(sampleViewModel, events)
  }
}
