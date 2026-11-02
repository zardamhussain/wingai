import SwiftUI

@main
struct WingAIiOSApp: App {
    var body: some Scene { WindowGroup { WingAiHostRoot() } }
}

@MainActor
struct WingAiHostRoot: View {
    @StateObject private var settings = WingAiSettings()
    @StateObject private var session = WingReplySession()
    @Environment(\.scenePhase) private var phase
    @State private var tab = 0

    var body: some View {
        WingAiTheme(settings: settings) {
            TabView(selection: $tab) {
                NavigationStack {
                    WingAiComposeScreen(settings: settings, session: session)
                        .navigationTitle("WingAI")
                }.tabItem { Label("Replies", systemImage: "sparkles") }.tag(0)
                NavigationStack {
                    WingAiAccountScreen(settings: settings, session: session)
                        .navigationTitle("Your WingAI")
                }.tabItem { Label("Your WingAI", systemImage: "person.crop.circle") }.tag(1)
                NavigationStack {
                    WingAiSetupScreen().navigationTitle("Get started")
                }.tabItem { Label("Setup", systemImage: "keyboard") }.tag(2)
            }
            // Avoid exposing pasted chats/screenshots in the app switcher's inactive snapshot.
            .overlay {
                if phase != .active {
                    Color(.systemBackground).ignoresSafeArea()
                        .overlay(Label("WingAI", systemImage: "sparkles").font(.largeTitle.bold()))
                }
            }
        }
        .onChange(of: phase) { newPhase in
            if newPhase == .active { settings.reload() } else { session.cancel() }
        }
        .onChange(of: settings.backendURL) { _ in session.forgetAccount() }
        .onChange(of: settings.hasServiceConsent) { accepted in if !accepted { session.cancel() } }
    }
}
