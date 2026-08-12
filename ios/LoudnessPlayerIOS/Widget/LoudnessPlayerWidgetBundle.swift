import SwiftUI
import WidgetKit

private struct PlaceholderEntry: TimelineEntry {
    let date: Date
}

private struct PlaceholderProvider: TimelineProvider {
    func placeholder(in context: Context) -> PlaceholderEntry { PlaceholderEntry(date: .now) }

    func getSnapshot(in context: Context, completion: @escaping (PlaceholderEntry) -> Void) {
        completion(PlaceholderEntry(date: .now))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PlaceholderEntry>) -> Void) {
        completion(Timeline(entries: [PlaceholderEntry(date: .now)], policy: .never))
    }
}

private struct PlaceholderWidgetView: View {
    let entry: PlaceholderEntry

    var body: some View {
        Text("音悦")
            .containerBackground(.fill.tertiary, for: .widget)
    }
}

private struct PlaceholderWidget: Widget {
    let kind = "LoudnessPlayerNowPlaying"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: PlaceholderProvider()) { entry in
            PlaceholderWidgetView(entry: entry)
        }
        .configurationDisplayName("音悦正在播放")
        .description("显示当前歌曲与歌词。")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct LoudnessPlayerWidgetBundle: WidgetBundle {
    var body: some Widget {
        PlaceholderWidget()
    }
}
