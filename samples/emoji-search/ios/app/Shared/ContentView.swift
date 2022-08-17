//
//  Copyright © Square Inc. All rights reserved.
//

import shared
import SwiftUI

struct ContentView: View {

    @State var searchTerm: String = ""
    @State var results: PresentersEmojiSearchViewModel?

    var body: some View {
        VStack {
            TextField("Filter", text: $searchTerm)
                .onChange(of: searchTerm) { newValue in
                    // Do stuff
                }
            LazyVStack {

            }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
