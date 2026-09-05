import SwiftUI
import UIKit
import ScheduleShared

@main
struct ScheduleAppiOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeScheduleView()
                .ignoresSafeArea()
        }
    }
}

private struct ComposeScheduleView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        ScheduleAppRoot.shared.makeViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
