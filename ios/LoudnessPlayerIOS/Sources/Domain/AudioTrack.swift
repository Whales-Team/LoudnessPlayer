import Foundation

enum AnalysisStatus: String, Codable, Sendable { case pending, running, succeeded, failed }

struct AudioTrack: Identifiable, Codable, Equatable, Sendable {
    let id: UUID
    var bookmark: Data
    var fileName: String
    var format: AudioFormat
    var title: String
    var artist: String
    var duration: TimeInterval
    var fileSize: Int64
    var contentFingerprint: String?
    var integratedLoudness: Double?
    var samplePeak: Double?
    var analysisStatus: AnalysisStatus
    var analysisMessage: String?
    var lrcText: String?

    init(
        id: UUID = UUID(), bookmark: Data, fileName: String, format: AudioFormat,
        title: String, artist: String, duration: TimeInterval, fileSize: Int64,
        contentFingerprint: String? = nil, integratedLoudness: Double? = nil,
        samplePeak: Double? = nil, analysisStatus: AnalysisStatus = .pending,
        analysisMessage: String? = nil, lrcText: String? = nil
    ) {
        self.id = id; self.bookmark = bookmark; self.fileName = fileName
        self.format = format; self.title = title; self.artist = artist
        self.duration = duration; self.fileSize = fileSize
        self.contentFingerprint = contentFingerprint
        self.integratedLoudness = integratedLoudness; self.samplePeak = samplePeak
        self.analysisStatus = analysisStatus; self.analysisMessage = analysisMessage
        self.lrcText = lrcText
    }
}
