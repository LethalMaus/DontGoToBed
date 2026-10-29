import ComposeApp
import SwiftUI
import UIKit

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        #if DEBUG
        let env = ProcessInfo.processInfo.environment
        if env["DGTB_IDEAL_CAPTURE"] == "1" {
            return MainViewControllerKt.CaptureViewController(
                scenario: Int32(env["DGTB_SCENARIO"] ?? "0") ?? 0,
                playerName: env["DGTB_PLAYER"] ?? "Leo",
                outfitName: env["DGTB_OUTFIT"] ?? "original",
                bed: Int32(env["DGTB_BED"] ?? "0") ?? 0,
                languageId: Int32(env["DGTB_LANGUAGE_ID"] ?? "1") ?? 1)
        }
        #endif
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .statusBarHidden(true)
    }
}
