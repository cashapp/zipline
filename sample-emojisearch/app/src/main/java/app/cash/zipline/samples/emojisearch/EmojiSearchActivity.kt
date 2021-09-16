package app.cash.zipline.samples.emojisearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class EmojiSearchActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // TODO: use the JS presenter
//    val emojiSearchZipline = EmojiSearchZipline()
//    val presenter = emojiSearchZipline.zipline.emojiSearchPresenter
    val presenter = EmojiSearchPresenterJVM(RealHostApi())
    val events = MutableSharedFlow<EmojiSearchEvent>(extraBufferCapacity = Int.MAX_VALUE)
    val models = MutableStateFlow(initialViewModel)

    CoroutineScope(EmptyCoroutineContext).launch {
      val modelReceiver = object : ModelReceiver<EmojiSearchViewModel> {
        override suspend fun invoke(model: EmojiSearchViewModel) {
          models.value = model
        }
      }
      presenter.produceModels(events, modelReceiver)
    }

    setContent {
      val modelsState = models.collectAsState()
      EmojiSearchTheme {
        EmojiSearch(modelsState.value) { event ->
          events.tryEmit(event)
        }
      }
    }
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
