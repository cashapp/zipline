import shared
import Foundation

final class HTTPClient: Zipline_loaderZiplineHttpClient {
    private let client: URLSession = .init(configuration: .default)

    func download(url: String, completionHandler: @escaping (OkioByteString?, Error?) -> Void) {
        let task = client.dataTask(with: URL(string: url)!) { data, response, error in
            // The KMM memory model doesn't do shared objects well, so Zipline expects the callback
            // on the same thread that the download was initiated from. This happens to be the main
            // thread, so we can bounce back to that thread for now.
            // Switching to the new KMM memory model may remove the need for this.
            DispatchQueue.main.async {
                completionHandler(data.map {
                    return ExposedKt.byteStringOf(data: $0)
                }, error)
            }
        }

        task.resume()
    }
}
