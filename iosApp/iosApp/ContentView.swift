import SwiftUI
import Shared
import WidgetKit

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
            .onReceive(
                NotificationCenter.default.publisher(for: NSNotification.Name("PeriodizeAIRefreshWidget"))
            ) { _ in
                WidgetCenter.shared.reloadAllTimelines()
            }
    }
}

// Bridge: renders the Compose Multiplatform App composable inside a SwiftUI view
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
