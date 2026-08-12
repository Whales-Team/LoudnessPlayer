import XCTest
@testable import LoudnessPlayer

final class LibraryOrganizerTests: XCTestCase {
    func testSearchesTitleAndArtistThenSortsByLocalizedTitle() {
        let tracks = [
            makeTrack(title: "夜曲", artist: "周杰伦"),
            makeTrack(title: "安静", artist: "周杰伦"),
            makeTrack(title: "Blue", artist: "The Band"),
        ]

        XCTAssertEqual(
            LibraryOrganizer.sorted(tracks, query: "周杰伦").map(\.title),
            ["安静", "夜曲"]
        )
        XCTAssertEqual(LibraryOrganizer.sorted(tracks, query: "blue").map(\.title), ["Blue"])
    }

    func testExistingRecordWinsOverHigherQualityNewDuplicate() {
        let existing = makeTrack(title: "烟花易冷", artist: "周杰伦", format: .mp3, duration: 360)
        let incoming = makeTrack(title: "烟花易冷", artist: "周杰伦", format: .flac, duration: 361)

        let retained = LibraryOrganizer.preferredDuplicates(existing: [existing], incoming: [incoming])

        XCTAssertEqual(retained, [existing])
    }

    func testNewDuplicatesPreferLosslessFormat() {
        let mp3 = makeTrack(title: "晴天", artist: "周杰伦", format: .mp3, duration: 269)
        let flac = makeTrack(title: "晴天", artist: "周杰伦", format: .flac, duration: 270)

        XCTAssertEqual(
            LibraryOrganizer.preferredDuplicates(existing: [], incoming: [mp3, flac]),
            [flac]
        )
    }

    func testArtistGroupingDoesNotMergeUnknownArtists() {
        let blank = makeTrack(title: "A", artist: "")
        let unknown = makeTrack(title: "B", artist: "未知歌手")
        let first = makeTrack(title: "C", artist: "周杰伦")
        let second = makeTrack(title: "D", artist: "周杰伦")

        let groups = LibraryOrganizer.groups(
            [blank, unknown, first, second], mode: .artistGroups, mergeSameArtist: true
        )

        XCTAssertEqual(groups.filter { $0.title == "周杰伦" }.first?.tracks.count, 2)
        XCTAssertEqual(groups.filter { $0.tracks.contains(blank) }.first?.tracks.count, 1)
        XCTAssertEqual(groups.filter { $0.tracks.contains(unknown) }.first?.tracks.count, 1)
    }

    func testTitleGroupingRequiresTwoSharedTokens() {
        let live = makeTrack(title: "晴天 现场版", artist: "周杰伦")
        let remaster = makeTrack(title: "晴天 现场 重制", artist: "周杰伦")
        let other = makeTrack(title: "晴天", artist: "其他")

        let groups = LibraryOrganizer.groups(
            [live, remaster, other], mode: .titleGroups, mergeSameArtist: false
        )

        XCTAssertTrue(groups.contains { Set($0.tracks.map(\.id)) == Set([live.id, remaster.id]) })
        XCTAssertTrue(groups.contains { $0.tracks == [other] })
    }
}

private func makeTrack(
    title: String,
    artist: String,
    format: AudioFormat = .mp3,
    duration: TimeInterval = 180
) -> AudioTrack {
    AudioTrack(
        id: UUID(), bookmark: Data(), fileName: "\(title).\(format.rawValue)", format: format,
        title: title, artist: artist, duration: duration, fileSize: 1_024
    )
}
