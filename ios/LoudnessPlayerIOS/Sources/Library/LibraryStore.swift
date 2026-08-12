import Foundation

enum LibraryStoreError: Error {
    case unsupportedSchema(Int)
}

actor LibraryStore {
    private let directory: URL
    private let fileManager: FileManager
    private let currentURL: URL
    private let previousURL: URL
    private let nextURL: URL
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    init(directory: URL, fileManager: FileManager = .default) {
        self.directory = directory
        self.fileManager = fileManager
        currentURL = directory.appendingPathComponent("library.json")
        previousURL = directory.appendingPathComponent("library.previous.json")
        nextURL = directory.appendingPathComponent("library.next.json")
        encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        decoder = JSONDecoder()
    }

    func load() throws -> LibraryDocument {
        guard fileManager.fileExists(atPath: currentURL.path) else {
            return try loadPrevious() ?? .empty
        }
        do {
            return try decodeDocument(at: currentURL)
        } catch {
            if let previous = try loadPrevious() { return previous }
            throw error
        }
    }

    func save(_ document: LibraryDocument) throws {
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let normalized = LibraryDocument(
            tracks: document.tracks,
            folders: document.folders,
            preferences: document.preferences
        )
        try encoder.encode(normalized).write(to: nextURL, options: [.atomic])

        if fileManager.fileExists(atPath: previousURL.path) {
            try fileManager.removeItem(at: previousURL)
        }
        if fileManager.fileExists(atPath: currentURL.path) {
            try fileManager.moveItem(at: currentURL, to: previousURL)
        }
        try fileManager.moveItem(at: nextURL, to: currentURL)
    }

    private func loadPrevious() throws -> LibraryDocument? {
        guard fileManager.fileExists(atPath: previousURL.path) else { return nil }
        return try decodeDocument(at: previousURL)
    }

    private func decodeDocument(at url: URL) throws -> LibraryDocument {
        let data = try Data(contentsOf: url)
        if let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
           let schema = object["schema"] as? Int,
           schema > LibraryDocument.currentSchema {
            throw LibraryStoreError.unsupportedSchema(schema)
        }
        return try decoder.decode(LibraryDocument.self, from: data)
    }
}
