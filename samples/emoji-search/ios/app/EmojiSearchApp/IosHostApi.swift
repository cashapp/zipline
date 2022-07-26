import Foundation
import shared

class IosHostApi : PresentersHostApi {
    private let client: URLSession = .init(configuration: .default)
    
    func httpCall(url: String, headers: [String : String], completionHandler: @escaping (String?, Error?) -> Void) {
        var request = URLRequest(url: URL(string: url)!)
        for (name, value) in headers {
            request.addValue(value, forHTTPHeaderField: name)
        }
        let task = client.dataTask(with: request) { data, response, error in
            // The KMM memory model doesn't do shared objects well, so Zipline expects the callback
            // on the same thread that the download was initiated from. This happens to be the main
            // thread, so we can bounce back to that thread for now.
            // Switching to the new KMM memory model may remove the need for this.
            DispatchQueue.main.async {
                completionHandler(data.map {
                    return String(decoding: $0, as: UTF8.self)
                }, error)
            }
        }

        task.resume()
    }
    
    func close() {
    }
}
