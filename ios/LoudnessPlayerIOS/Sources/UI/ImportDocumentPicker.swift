import SwiftUI
import UniformTypeIdentifiers
import UIKit

struct ImportDocumentPicker: UIViewControllerRepresentable {
    enum Kind { case files, directory }

    let kind: Kind
    let onSelect: ([URL]) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let types: [UTType]
        switch kind {
        case .directory: types = [.folder]
        case .files:
            types = [.audio] + ["ape", "wma"].compactMap { UTType(filenameExtension: $0) }
        }
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: false)
        picker.allowsMultipleSelection = kind == .files
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let parent: ImportDocumentPicker
        init(parent: ImportDocumentPicker) { self.parent = parent }
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            parent.onSelect(urls)
        }
        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { parent.onCancel() }
    }
}
