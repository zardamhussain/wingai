import SwiftUI

@MainActor
public final class WingAiSettings: ObservableObject {
    private let store: AppGroupStore
    @Published public var appearance: WingAppearance {
        didSet { store.appearance = appearance }
    }
    @Published public private(set) var backendURL: String
    @Published public private(set) var hasServiceConsent: Bool
    public var usesSharedGroup: Bool { store.usesSharedGroup }
    public var userID: String { store.getOrCreateUserId() }

    public init(store: AppGroupStore = .shared) {
        self.store = store
        appearance = store.appearance
        backendURL = store.backendUrl
        hasServiceConsent = store.consentedBackend == store.backendUrl
    }

    public func reload() {
        appearance = store.appearance
        backendURL = store.backendUrl
        hasServiceConsent = store.consentedBackend == store.backendUrl
    }

    @discardableResult
    public func saveBackend(_ value: String) -> Bool {
        var clean = value.trimmingCharacters(in: .whitespacesAndNewlines)
        while clean.hasSuffix("/") { clean.removeLast() }
        guard WingAiApiClient.isValidBackend(clean) else { return false }
        if clean != backendURL {
            store.consentedBackend = nil
            hasServiceConsent = false
        }
        store.backendUrl = clean
        backendURL = clean
        return true
    }

    public func setServiceConsent(_ accepted: Bool) {
        hasServiceConsent = accepted
        store.consentedBackend = accepted ? backendURL : nil
    }
}
