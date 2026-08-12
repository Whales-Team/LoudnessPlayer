import Foundation

enum LibraryOrganizer {
    static func sorted(_ tracks: [AudioTrack], query: String = "") -> [AudioTrack] {
        let query = normalize(query)
        return tracks.filter {
            query.isEmpty || normalize($0.title).contains(query) || normalize($0.artist).contains(query)
        }.sorted {
            let order = alphabeticalKey($0.title).localizedStandardCompare(alphabeticalKey($1.title))
            if order != .orderedSame { return order == .orderedAscending }
            if $0.artist.isEmpty != $1.artist.isEmpty { return !$0.artist.isEmpty }
            return alphabeticalKey($0.artist).localizedStandardCompare(alphabeticalKey($1.artist)) == .orderedAscending
        }
    }

    static func preferredDuplicates(existing: [AudioTrack], incoming: [AudioTrack]) -> [AudioTrack] {
        var retained = existing
        for candidate in incoming.sorted(by: { $0.format.qualityRank > $1.format.qualityRank })
            where !retained.contains(where: { isDuplicate($0, candidate) }) {
            retained.append(candidate)
        }
        return retained
    }

    static func groups(_ tracks: [AudioTrack], mode: LibraryViewMode, mergeSameArtist: Bool) -> [TrackGroup] {
        if mode == .artistGroups, mergeSameArtist { return artistGroups(tracks) }
        if mode == .titleGroups { return titleGroups(tracks) }
        return sorted(tracks).map { TrackGroup(id: $0.id.uuidString, title: $0.title, tracks: [$0]) }
    }

    private static func artistGroups(_ tracks: [AudioTrack]) -> [TrackGroup] {
        var known: [String: [AudioTrack]] = [:]
        var result: [TrackGroup] = []
        for track in tracks {
            let key = normalize(track.artist)
            if key.isEmpty || key == normalize("未知歌手") {
                result.append(TrackGroup(id: track.id.uuidString, title: track.title, tracks: [track]))
            } else { known[key, default: []].append(track) }
        }
        result += known.map { key, members in
            TrackGroup(id: "artist:\(key)", title: members[0].artist, tracks: sorted(members))
        }
        return result.sorted { $0.title.localizedStandardCompare($1.title) == .orderedAscending }
    }

    private static func titleGroups(_ tracks: [AudioTrack]) -> [TrackGroup] {
        var remaining = tracks
        var result: [TrackGroup] = []
        while !remaining.isEmpty {
            let anchor = remaining.removeFirst()
            let anchorTokens = tokens(anchor)
            let matches = remaining.filter { anchorTokens.intersection(tokens($0)).count >= 2 }
            let ids = Set(matches.map(\.id))
            remaining.removeAll { ids.contains($0.id) }
            result.append(TrackGroup(id: "title:\(anchor.id)", title: anchor.title, tracks: sorted([anchor] + matches)))
        }
        return result
    }

    private static func isDuplicate(_ left: AudioTrack, _ right: AudioTrack) -> Bool {
        normalize(left.title) == normalize(right.title)
            && normalize(left.artist) == normalize(right.artist)
            && abs(left.duration - right.duration) <= 2
    }

    private static func tokens(_ track: AudioTrack) -> Set<String> {
        Set("\(track.title) \(track.artist)".lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .map { normalize($0) }.filter { $0.count >= 2 })
    }

    private static func normalize(_ value: String) -> String {
        value.folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: .current)
            .trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private static func alphabeticalKey(_ value: String) -> String {
        let mutable = NSMutableString(string: value)
        CFStringTransform(mutable, nil, kCFStringTransformToLatin, false)
        CFStringTransform(mutable, nil, kCFStringTransformStripCombiningMarks, false)
        return normalize(mutable as String)
    }
}
