import shared
import SwiftUI

@main
struct WorldClockApp: App {
    var contentView = ContentView()

    var body: some Scene {
        WindowGroup {
            contentView
        }
    }
}

struct ContentView: View {
    @State private var label = "..."
    @State var tapCount = 0

    var body: some View {
        Text(label)
            .padding()

        .onAppear {
            let worldClockIos = PresentersWorldClockIos(httpClient: HTTPClient())
            worldClockIos.start { model in
                self.label = model.label
            }
        }
    }
}
