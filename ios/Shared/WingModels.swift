import Foundation

public enum WingTone: String, CaseIterable, Codable, Sendable {
    case casual = "casual"
    case friendly = "friendly"
    case professional = "professional"
    case funny = "funny"
    case flirty = "flirty"
    case short = "short"
    case detailed = "detailed"

    public var displayName: String {
        rawValue.capitalized
    }
}

public struct ReplyItem: Codable, Equatable, Identifiable, Sendable {
    public var id: String { "\(tone):\(text)" }
    public let tone: String
    public let text: String

    public init(tone: String, text: String) {
        self.tone = tone
        self.text = text
    }
}

public struct UserProfile: Codable, Equatable, Sendable {
    public let userId: String
    public let tier: String
    public let dailyUsed: Int
    public let dailyLimit: Int?
    public let remaining: Int?
    public let usageDate: String

    public var isPaid: Bool {
        tier.lowercased() == "paid"
    }

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case tier
        case dailyUsed = "daily_used"
        case dailyLimit = "daily_limit"
        case remaining
        case usageDate = "usage_date"
    }

    public init(
        userId: String,
        tier: String = "free",
        dailyUsed: Int = 0,
        dailyLimit: Int? = 5,
        remaining: Int? = 5,
        usageDate: String = ""
    ) {
        self.userId = userId
        self.tier = tier
        self.dailyUsed = dailyUsed
        self.dailyLimit = dailyLimit
        self.remaining = remaining
        self.usageDate = usageDate
    }
}

public struct ReplyResponse: Codable, Equatable, Sendable {
    public let status: String
    public let contextStatus: String
    public let message: String?
    public let replies: [ReplyItem]
    public let user: UserProfile?

    enum CodingKeys: String, CodingKey {
        case status
        case contextStatus = "context_status"
        case message
        case replies
        case user
    }
}

/// Preferences only: conversation text, screenshots and replies are never saved here.
/// App Groups are optional; without a correctly entitled group each target works locally.
public final class AppGroupStore: @unchecked Sendable {
    public static let shared = AppGroupStore()
    public static let defaultBackendUrl = "https://wingai-backend.zardamhussain.workers.dev"
    private let defaults: UserDefaults
    private let lock = NSLock()
    public let usesSharedGroup: Bool

    public init(defaults: UserDefaults? = nil) {
        if let defaults {
            self.defaults = defaults
            self.usesSharedGroup = false
            return
        }
        let group = (Bundle.main.object(forInfoDictionaryKey: "WingAIAppGroup") as? String ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        #if os(iOS) || os(macOS)
        if !group.isEmpty,
           FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) != nil,
           let shared = UserDefaults(suiteName: group) {
            self.defaults = shared
            self.usesSharedGroup = true
            return
        }
        #endif
        self.defaults = .standard
        self.usesSharedGroup = false
    }

    public func getOrCreateUserId() -> String {
        lock.lock()
        defer { lock.unlock() }
        let userIdKey = "wingai_device_user_id"
        if let existing = defaults.string(forKey: userIdKey), !existing.isEmpty {
            return existing
        }
        let rawUuid = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        let generated = "wing_\(rawUuid.prefix(12))"
        defaults.set(generated, forKey: userIdKey)
        return generated
    }

    public var backendUrl: String {
        get {
            let stored = defaults.string(forKey: "wingai_backend_url")?.trimmingCharacters(in: .whitespacesAndNewlines)
            return (stored?.isEmpty == false) ? stored! : Self.defaultBackendUrl
        }
        set {
            defaults.set(newValue, forKey: "wingai_backend_url")
        }
    }

    public var appearance: WingAppearance {
        get { WingAppearance.from(defaults.string(forKey: "wingai_appearance")) }
        set { defaults.set(newValue.rawValue, forKey: "wingai_appearance") }
    }

    /// Consent is tied to the exact endpoint; changing it requires a fresh acknowledgement.
    public var consentedBackend: String? {
        get { defaults.string(forKey: "wingai_consented_backend") }
        set { defaults.set(newValue, forKey: "wingai_consented_backend") }
    }
}
