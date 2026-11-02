import SwiftUI

public extension Color {
    init(wingHex: UInt32) {
        self.init(.sRGB, red: Double((wingHex >> 16) & 255) / 255,
                  green: Double((wingHex >> 8) & 255) / 255,
                  blue: Double(wingHex & 255) / 255, opacity: 1)
    }
}

public struct WingAiPalette {
    public let bgCanvas: Color
    public let surface1: Color
    public let surface2: Color
    public let borderSubtle: Color
    public let accentPrimary: Color
    public let onAccentPrimary: Color
    public let accentPrimaryContainer: Color
    public let textPrimary: Color
    public let textSecondary: Color
    public let accentRose: Color

    public static func palette(_ appearance: WingAppearance, scheme: ColorScheme) -> WingAiPalette {
        let choice = appearance == .system ? (scheme == .dark ? WingAppearance.aurora : .daylight) : appearance
        switch choice {
        case .system, .aurora:
            return make(0x11101B, 0x1D1B2E, 0x29253E, 0x3B3554, 0xC4B5FD, 0x21143D, 0x342650, 0xF5F2FF, 0xBEB7D4, 0xFFA4B9)
        case .daylight:
            return make(0xF5F6FB, 0xFFFFFF, 0xEAEDF6, 0xD2D7E7, 0x5145B8, 0xFFFFFF, 0xE9E5FC, 0x202139, 0x5D6179, 0xAC284B)
        case .ocean:
            return make(0x0B1923, 0x132A37, 0x1C3947, 0x305362, 0x7DDED0, 0x082F30, 0x164942, 0xEAF8FA, 0xA8C3CE, 0xFFA4B9)
        case .rose:
            return make(0xFCF4F3, 0xFFFAF9, 0xF4E6E7, 0xE5CBCD, 0xA33357, 0xFFFFFF, 0xF8DDE5, 0x39222D, 0x795A65, 0xAC284B)
        }
    }

    private static func make(_ canvas: UInt32, _ surface: UInt32, _ raised: UInt32, _ border: UInt32,
                             _ accent: UInt32, _ onAccent: UInt32, _ container: UInt32,
                             _ text: UInt32, _ secondary: UInt32, _ error: UInt32) -> WingAiPalette {
        WingAiPalette(bgCanvas: Color(wingHex: canvas), surface1: Color(wingHex: surface),
                      surface2: Color(wingHex: raised), borderSubtle: Color(wingHex: border),
                      accentPrimary: Color(wingHex: accent), onAccentPrimary: Color(wingHex: onAccent),
                      accentPrimaryContainer: Color(wingHex: container), textPrimary: Color(wingHex: text),
                      textSecondary: Color(wingHex: secondary), accentRose: Color(wingHex: error))
    }
}

private struct WingPaletteKey: EnvironmentKey {
    static let defaultValue = WingAiPalette.palette(.aurora, scheme: .dark)
}
public extension EnvironmentValues {
    var wingPalette: WingAiPalette {
        get { self[WingPaletteKey.self] }
        set { self[WingPaletteKey.self] = newValue }
    }
}

/// Do not hard-code screen sizes: iPhone safe areas, Dynamic Island and rotation are system-owned.
public struct WingAiTheme<Content: View>: View {
    @Environment(\.colorScheme) private var systemScheme
    @ObservedObject private var settings: WingAiSettings
    private let content: Content

    public init(settings: WingAiSettings, @ViewBuilder content: () -> Content) {
        self.settings = settings
        self.content = content()
    }
    public var body: some View {
        let choice = settings.appearance
        let scheme: ColorScheme? = choice == .system ? nil : ([.aurora, .ocean].contains(choice) ? .dark : .light)
        let palette = WingAiPalette.palette(choice, scheme: scheme ?? systemScheme)
        content
            .environment(\.wingPalette, palette)
            .tint(palette.accentPrimary)
            .foregroundColor(palette.textPrimary)
            .preferredColorScheme(scheme)
    }
}

public struct WingAiSurfaceCard<Content: View>: View {
    @Environment(\.wingPalette) private var palette
    private let content: Content
    public init(@ViewBuilder content: () -> Content) { self.content = content() }
    public var body: some View {
        VStack(alignment: .leading, spacing: 12) { content }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(palette.surface1)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(palette.borderSubtle))
    }
}

public struct CompactPillButton: View {
    @Environment(\.wingPalette) private var palette
    public let title: String
    public var isPrimary = false
    public var isDisabled = false
    public let action: () -> Void
    public init(title: String, isPrimary: Bool = false, isDisabled: Bool = false, action: @escaping () -> Void) {
        self.title = title; self.isPrimary = isPrimary; self.isDisabled = isDisabled; self.action = action
    }
    public var body: some View {
        Button(action: action) {
            Text(title).font(.subheadline.weight(.semibold))
                .foregroundColor(isPrimary ? palette.onAccentPrimary : palette.textPrimary)
                .padding(.horizontal, 14).frame(minHeight: 44)
                .background(isPrimary ? palette.accentPrimary : palette.surface2)
                .clipShape(Capsule())
        }.buttonStyle(.plain).disabled(isDisabled).opacity(isDisabled ? 0.5 : 1)
    }
}

public struct WingAiThemePicker: View {
    @Environment(\.wingPalette) private var palette
    @Environment(\.colorScheme) private var scheme
    @ObservedObject public var settings: WingAiSettings
    public init(settings: WingAiSettings) { self.settings = settings }
    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Make it yours.").font(.title2.bold())
            Text("A fresh look for the way you connect.").font(.subheadline).foregroundColor(palette.textSecondary)
            ForEach(WingAppearance.allCases, id: \.rawValue) { appearance in
                let swatch = WingAiPalette.palette(appearance, scheme: scheme)
                Button { settings.appearance = appearance } label: {
                    HStack(spacing: 12) {
                        Circle().fill(swatch.accentPrimary).frame(width: 28, height: 28)
                            .overlay(Circle().stroke(swatch.borderSubtle))
                        VStack(alignment: .leading, spacing: 3) {
                            Text(appearance.title).font(.headline)
                            Text(appearance.subtitle).font(.caption).foregroundColor(swatch.textSecondary)
                        }
                        Spacer(minLength: 4)
                        if settings.appearance == appearance { Image(systemName: "checkmark.circle.fill").foregroundColor(swatch.accentPrimary) }
                    }
                    .foregroundColor(swatch.textPrimary).padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(swatch.surface1)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(settings.appearance == appearance ? swatch.accentPrimary : swatch.borderSubtle, lineWidth: 1.5))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(appearance.title), \(appearance.subtitle)")
                .accessibilityAddTraits(settings.appearance == appearance ? .isSelected : [])
            }
        }
    }
}
