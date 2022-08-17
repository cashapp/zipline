import Foundation
import UIKit
import shared

class EmojiSearchViewController : UIViewController, UITableViewDataSource {

    // MARK: - Private Properties

    private let mainView = View()
    private let emojiSearchZipline = PresentersEmojiSearchZipline(httpClient: HTTPClient(), hostApi: IosHostApi())

    private var results: PresentersEmojiSearchViewModel?

    private let remoteImageLoader = RemoteImageLoader()

    // MARK: - UIViewController

    override func loadView() {
        view = mainView

        emojiSearchZipline.setUp { [unowned self] results in
            self.results = results
            self.mainView.resultsTableView.reloadData()
        }

        mainView.resultsTableView.dataSource = self
    }

    override func viewDidLoad() {
        mainView.searchField.addAction(
            .init(handler: { [unowned self] _ in
                self.emojiSearchZipline.updateSearchTerm(searchTerm: mainView.searchField.text ?? "")
            }),
            for: .editingChanged
        )
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)

        mainView.searchField.becomeFirstResponder()
    }

    // MARK: - UITableViewDataSource

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        results?.images.count ?? 0
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        return ResultsTableViewCell(
            result: results!.images[indexPath.row],
            remoteImageLoader: remoteImageLoader,
            reuseIdentifier: "Result"
        )
    }

}

extension EmojiSearchViewController {

    fileprivate final class View: UIView {

        // MARK: - Properties

        let searchField: UITextField = .init()
        let resultsTableView: UITableView = .init()

        // MARK: - Initialization

        override init(frame: CGRect = .zero) {
            super.init(frame: frame)

            addSubview(searchField)
            addSubview(resultsTableView)

            searchField.borderStyle = .roundedRect
            searchField.autocapitalizationType = .none

            resultsTableView.rowHeight = 60

            backgroundColor = .white
        }

        required init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }

        // MARK: - UIView

        override func layoutSubviews() {
            let contentRect = bounds.inset(by: layoutMargins)

            searchField.sizeToFit()
            searchField.frame.size.width = contentRect.width
            searchField.frame.origin = .init(
                x: contentRect.minX,
                y: contentRect.minY
            )

            resultsTableView.frame = .init(
                x: 0,
                y: searchField.frame.maxY + 12,
                width: bounds.width,
                height: bounds.height - searchField.frame.maxY
            )
        }
    }

    final class ResultsTableViewCell: UITableViewCell {

        private let iconImageView: UIImageView = .init()
        private let titleLabel: UILabel = .init()

        private let loadImage: () -> Void

        required init(
            result: PresentersEmojiImage,
            remoteImageLoader: RemoteImageLoader,
            reuseIdentifier: String?
        ) {
            loadImage = { [weak iconImageView] in
                guard let url = URL(string: result.url) else { return }
                remoteImageLoader.loadImage(url: url) { url, image in
                    iconImageView?.image = image
                }
            }

            super.init(style: .default, reuseIdentifier: reuseIdentifier)

            contentView.addSubview(iconImageView)
            contentView.addSubview(titleLabel)

            iconImageView.contentMode = .scaleAspectFit
            titleLabel.text = result.displayableLabel
        }

        required init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }

        override func layoutSubviews() {
            super.layoutSubviews()

            let imageToTitleHorizontalSpacing: CGFloat = 8

            let contentRect = contentView.bounds.inset(by: layoutMargins)
            let iconSize = CGSize(width: contentRect.height, height: contentRect.height)

            iconImageView.frame = .init(
                x: contentRect.minX,
                y: contentRect.minY,
                width: iconSize.width,
                height: iconSize.height
            )

            titleLabel.frame = .init(
                x: iconImageView.frame.maxX + imageToTitleHorizontalSpacing,
                y: contentRect.minY,
                width: contentRect.maxX - (iconImageView.frame.maxX + imageToTitleHorizontalSpacing),
                height: contentRect.height
            )
        }

        override func willMove(toSuperview newSuperview: UIView?) {
            loadImage()
        }
    }

}

extension PresentersEmojiImage {
    fileprivate var displayableLabel: String {
        return label.replacingOccurrences(of: "_", with: " ").capitalized
    }
}
