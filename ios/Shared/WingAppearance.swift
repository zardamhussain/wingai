import Foundation

/// Stable IDs shared with the Android UI refresh. System follows the iPhone appearance.
public enum WingAppearance: String, CaseIterable, Codable, Sendable {
    case system, aurora, daylight, ocean, rose

    public var title: String { self == .system ? "Follow iPhone" : rawValue.capitalized }
    public var subtitle: String {
        switch self {
        case .system: return "Match light or dark mode"
        case .aurora: return "Soft violet, after dark"
        case .daylight: return "Airy, bright and focused"
        case .ocean: return "Deep blue with fresh mint"
        case .rose: return "Warm blush, soft contrast"
        }
    }
    public static func from(_ value: String?) -> WingAppearance {
        value.flatMap(Self.init(rawValue:)) ?? .system
    }
}
