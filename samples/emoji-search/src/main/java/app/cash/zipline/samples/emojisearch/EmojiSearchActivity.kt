package app.cash.zipline.samples.emojisearch

import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.cash.zipline.toFlowReference
import coil.compose.rememberImagePainter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect

@NoLiveLiterals
class EmojiSearchActivity : ComponentActivity() {
  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val tp = StrictMode.ThreadPolicy.LAX
    StrictMode.setThreadPolicy(tp)



    // TODO: use the JS presenter
    val emojiSearchZipline = EmojiSearchZipline(Dispatchers.Main)
    val presenter = emojiSearchZipline.zipline.emojiSearchPresenter
    val events = MutableSharedFlow<EmojiSearchEvent>(extraBufferCapacity = Int.MAX_VALUE)
    val models = MutableStateFlow(initialViewModel)

    scope.launch {
      val eventsFlowReference = events.toFlowReference(EmojiSearchEvent.serializer())
      val modelsReference = presenter.produceModels(eventsFlowReference)

      val returnedModels = modelsReference.get(EmojiSearchViewModel.serializer())
      println("COLLECTING MODELS...")
      returnedModels.collect {
        println("RECEIVED MODEL, EMITTING")
        models.emit(it)
        println("DONE EMITTING MODEL ")
      }
      println("DONE COLLECTING MODELS...")
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
