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
            .font(.system(size: 36))
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)

        .onAppear {
            let scope = ExposedKt.mainScope()
            let worldClockIos = PresentersWorldClockIos(httpClient: HTTPClient(), scope: scope)
            worldClockIos.start { model in
                self.label = model.label
            }
        }
    }
}
