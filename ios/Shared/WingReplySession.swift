import SwiftUI

/// One cancellable request at a time. Typing, changing tone or pasting never uploads by itself.
@MainActor
public final class WingReplySession: ObservableObject {
    @Published public var context = ""
    @Published public var selectedTone: WingTone? = nil
    @Published public var instruction = ""
    @Published public var screenshotJPEG: Data? = nil
    @Published public private(set) var replies: [ReplyItem] = []
    @Published public private(set) var profile: UserProfile? = nil
    @Published public private(set) var isLoading = false
    @Published public var error: String? = nil
    @Published public private(set) var isSample = false
    private var requestTask: Task<Void, Never>?
    private var requestID = UUID()

    public init() {}
    public var hasContext: Bool { !context.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || screenshotJPEG != nil }

    public func cancel() {
        requestID = UUID()
        requestTask?.cancel()
        requestTask = nil
        isLoading = false
    }
    public func clear() {
        cancel(); context = ""; screenshotJPEG = nil; instruction = ""; replies = []; error = nil; isSample = false
    }
    public func forgetAccount() { cancel(); profile = nil }
    public func loadSample() {
        clear()
        context = "Jamie: Are we still on for coffee at 4?"
        replies = [ReplyItem(tone: "friendly", text: "Yes! Really looking forward to catching up at 4."),
                   ReplyItem(tone: "casual", text: "Yep, 4 works! See you there."),
                   ReplyItem(tone: "professional", text: "Yes, 4:00 PM still works well for me. See you soon.")]
        isSample = true
    }
    public func generate(settings: WingAiSettings, networkAllowed: Bool = true) {
        guard networkAllowed else { error = "Enable Allow Full Access for online replies. Ordinary typing works without it."; return }
        guard settings.hasServiceConsent else { error = "Review the service and enable online AI before generating replies."; return }
        guard hasContext else { error = "Paste a conversation or choose a screenshot first."; return }
        // The Worker measures JavaScript string.length: UTF-16, not Swift grapheme count.
        guard context.utf16.count <= 12_000, instruction.utf16.count <= 500,
              (screenshotJPEG?.count ?? 0) <= 2_000_000 else {
            error = "Use a shorter chat (up to 12,000 characters), instruction (500) or smaller image (2 MB). Some emoji count as more than one character."; return
        }
        cancel()
        error = nil; isLoading = true; isSample = false; replies = []
        let id = requestID
        let text = context, image = screenshotJPEG?.base64EncodedString(), tone = selectedTone?.rawValue, prompt = instruction
        // Freeze consented endpoint and identity for this request, not mutable UI preferences.
        let endpoint = settings.backendURL, userID = settings.userID
        let client = WingAiApiClient(baseUrlProvider: { endpoint }, userIdProvider: { userID })
        requestTask = Task { [weak self] in
            do {
                let response = try await client.analyzeContext(imageBase64: image, conversationText: text, tone: tone, customInstruction: prompt)
                guard !Task.isCancelled, let self, self.requestID == id else { return }
                self.replies = response.replies
                self.profile = response.user
                self.isLoading = false
                if response.replies.isEmpty || response.contextStatus == "insufficient" {
                    self.error = response.message ?? "Try a clearer conversation or a different screenshot."
                }
            } catch {
                guard !Task.isCancelled, let self, self.requestID == id else { return }
                self.error = error.localizedDescription
                self.isLoading = false
            }
        }
    }
    public func refreshProfile(settings: WingAiSettings) {
        guard settings.hasServiceConsent else { error = "Enable online AI after reviewing the service to check your plan."; return }
        cancel(); isLoading = true; error = nil
        let id = requestID, endpoint = settings.backendURL, userID = settings.userID
        let client = WingAiApiClient(baseUrlProvider: { endpoint }, userIdProvider: { userID })
        requestTask = Task { [weak self] in
            do {
                let profile = try await client.fetchUserProfile()
                guard !Task.isCancelled, let self, self.requestID == id else { return }
                self.profile = profile; self.isLoading = false
            } catch {
                guard !Task.isCancelled, let self, self.requestID == id else { return }
                self.error = error.localizedDescription; self.isLoading = false
            }
        }
    }
}

public struct WingServiceConsentView: View {
    @Environment(\.wingPalette) private var palette
    @ObservedObject public var settings: WingAiSettings
    public init(settings: WingAiSettings) { self.settings = settings }
    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("You're in control.", systemImage: "hand.raised").font(.headline)
            Text(settings.backendURL).font(.caption).textSelection(.enabled)
            Text("Analyze chat sends the chat or screenshot you choose, your instructions and a device ID to this service and its AI provider. The service may store requests. Review its privacy terms before sharing personal content.")
                .font(.caption).foregroundColor(palette.textSecondary)
            Toggle("Enable online AI for this service", isOn: Binding(
                get: { settings.hasServiceConsent }, set: { settings.setServiceConsent($0) }
            )).font(.subheadline)
            Text("No messages are sent automatically. Pasting, choosing a photo and changing a theme do not generate a request.")
                .font(.caption2).foregroundColor(palette.textSecondary)
        }
    }
}
