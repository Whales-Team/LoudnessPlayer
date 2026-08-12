import Foundation

enum BookmarkError: Error, Equatable {
    case accessDenied
}

protocol SecurityResourceAccess: Sendable {
    func start(url: URL) -> Bool
    func stop(url: URL)
}

struct SystemSecurityResourceAccess: SecurityResourceAccess {
    func start(url: URL) -> Bool { url.startAccessingSecurityScopedResource() }
    func stop(url: URL) { url.stopAccessingSecurityScopedResource() }
}

struct ResolvedBookmark: Sendable {
    let url: URL
    let isStale: Bool
    private let access: any SecurityResourceAccess

    init(url: URL, isStale: Bool, access: any SecurityResourceAccess = SystemSecurityResourceAccess()) {
        self.url = url
        self.isStale = isStale
        self.access = access
    }

    func withAccess<T>(_ operation: (URL) throws -> T) throws -> T {
        guard access.start(url: url) else { throw BookmarkError.accessDenied }
        defer { access.stop(url: url) }
        return try operation(url)
    }
}

protocol BookmarkResolving: Sendable {
    func resolve(_ bookmark: Data) throws -> ResolvedBookmark
}

struct BookmarkResolver: BookmarkResolving {
    func resolve(_ bookmark: Data) throws -> ResolvedBookmark {
        var isStale = false
        let url = try URL(
            resolvingBookmarkData: bookmark,
            options: [.withoutUI],
            relativeTo: nil,
            bookmarkDataIsStale: &isStale
        )
        return ResolvedBookmark(url: url, isStale: isStale)
    }
}
