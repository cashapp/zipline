//
//  Copyright © 2022 Square Inc. All rights reserved.
//

import Foundation
import UIKit

final class RemoteImageLoader {

    private var cache: [URL: UIImage] = [:]

    func loadImage(url: URL, completion: @escaping (URL, UIImage) -> Void) {
        if let image = cache[url] {
            completion(url, image)
            return
        }

        // This is not a good image loader, but it's a good-enough image loader.
        DispatchQueue.global().async { [weak self] in
            guard let data = try? Data(contentsOf: url) else { return }
            guard let image = UIImage(data: data) else { return }
            DispatchQueue.main.async {
                self?.cache[url] = image
                completion(url, image)
            }
        }
    }
}
