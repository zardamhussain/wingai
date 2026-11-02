import SwiftUI
import UIKit

@MainActor
public final class WingAiKeyboardViewModel: ObservableObject {
    @Published public var hasFullAccess = false
    @Published public var needsGlobe = true
    @Published public var isAiPanelOpen = false
    @Published public var showsThemes = false
    @Published public var shifted = false
    @Published public var capsLock = false
    @Published public var symbolPage = 0
    public init() {}
}

/// A real textDocumentProxy keyboard, not a simulated messenger. No screenshot/host-screen access.
@MainActor
public struct WingAiKeyboardRootView: View {
    @Environment(\.wingPalette) private var palette
    @ObservedObject var state: WingAiKeyboardViewModel
    @ObservedObject var session: WingReplySession
    @ObservedObject var settings: WingAiSettings
    let onInsert: (String) -> Void
    let onDelete: () -> Void
    let onGlobeEvent: (UIView, UIEvent) -> Void
    let onDismiss: () -> Void
    let onPaste: () -> String?
    let onGenerate: () -> Void

    private let letters = [Array("qwertyuiop"), Array("asdfghjkl"), Array("zxcvbnm")]
    private let numbers = [Array("1234567890"), Array("-/:;()$&@\""), Array(".,?!'#%")]
    private let symbols = [Array("[]{}#%^*+="), Array("_\\|~<>€£¥•"), Array(".,?!'`;")]

    public var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 8) {
                Label("wingai", systemImage: "sparkles").font(.subheadline.bold()).foregroundColor(palette.accentPrimary)
                Spacer(minLength: 0)
                Button { state.showsThemes.toggle() } label: {
                    Image(systemName: "paintpalette").frame(width: 40, height: 40)
                }.accessibilityLabel("Keyboard themes")
                Button {
                    state.showsThemes = false; state.isAiPanelOpen.toggle()
                } label: {
                    Text(state.isAiPanelOpen ? "Keys" : "Replies").font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 12).frame(minHeight: 40)
                        .background(palette.accentPrimaryContainer).clipShape(Capsule())
                }
            }
            .buttonStyle(.plain)

            if state.showsThemes {
                ScrollView { WingAiThemePicker(settings: settings).padding(4) }
            } else if state.isAiPanelOpen {
                replyPanel
            } else {
                keyGrid
            }

            // The switcher remains accessible from keys, replies, errors and theme selection.
            HStack(spacing: 8) {
                if state.needsGlobe {
                    WingGlobeButton(tint: UIColor(palette.textPrimary), onEvent: onGlobeEvent)
                        .frame(width: 48, height: 40)
                }
                Text(state.hasFullAccess ? "Online AI requires your consent" : "Offline typing · Full Access off")
                    .font(.system(size: 10)).foregroundColor(palette.textSecondary).lineLimit(2)
                Spacer(minLength: 0)
                Button(action: onDismiss) { Image(systemName: "keyboard.chevron.compact.down").frame(width: 44, height: 40) }
                    .buttonStyle(.plain).accessibilityLabel("Hide keyboard")
            }
        }
        .padding(.horizontal, 6).padding(.top, 4)
        .background(palette.bgCanvas)
        .onChange(of: settings.hasServiceConsent) { allowed in if !allowed { session.cancel() } }
        .onChange(of: state.hasFullAccess) { allowed in if !allowed { session.clear() } }
        .onChange(of: settings.backendURL) { _ in session.forgetAccount() }
    }

    private var replyPanel: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                if !state.hasFullAccess {
                    Text("Your keyboard works offline.").font(.headline)
                    Text("For paste and online AI, enable Allow Full Access in iPhone Settings > General > Keyboard > Keyboards > WingAI. No chat is read automatically.")
                        .font(.caption).foregroundColor(palette.textSecondary)
                } else {
                    HStack {
                        CompactPillButton(title: "Paste chat") {
                            session.cancel()
                            if let text = onPaste(), !text.isEmpty { session.context = text; session.error = nil }
                            else { session.error = "Copy a conversation first. No clipboard text was available." }
                        }
                        CompactPillButton(title: "Clear") { session.clear() }
                    }
                    if !session.context.isEmpty {
                        WingAiSurfaceCard {
                            Text("Context to send").font(.caption.bold())
                            Text(session.context).font(.caption).textSelection(.enabled)
                            Text("\(session.context.count) / 12,000 characters").font(.caption2)
                        }
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            toneButton(nil)
                            ForEach(WingTone.allCases, id: \.rawValue) { tone in toneButton(tone) }
                        }
                    }
                    if !settings.hasServiceConsent { WingServiceConsentView(settings: settings) }
                    if session.isLoading {
                        HStack { ProgressView("Finding the words..."); CompactPillButton(title: "Cancel") { session.cancel() } }
                    } else {
                        CompactPillButton(title: "Analyze chat", isPrimary: true,
                                          isDisabled: !settings.hasServiceConsent || !session.hasContext) { onGenerate() }
                    }
                    Text("Screenshots: select one in the WingAI app instead. A keyboard cannot see the conversation above it.")
                        .font(.caption2).foregroundColor(palette.textSecondary)
                }
                if let error = session.error { Text(error).font(.caption).foregroundColor(palette.accentRose) }
                ForEach(Array(session.replies.enumerated()), id: \.offset) { _, reply in
                    WingAiSurfaceCard {
                        Text(reply.tone.capitalized).font(.caption.bold()).foregroundColor(palette.accentPrimary)
                        Text(reply.text).font(.subheadline)
                        CompactPillButton(title: "Insert", isPrimary: true) {
                            onInsert(reply.text); state.isAiPanelOpen = false
                        }
                    }
                }
            }.padding(.vertical, 4)
        }
    }

    private func toneButton(_ tone: WingTone?) -> some View {
        CompactPillButton(title: tone?.displayName ?? "Mix it up", isPrimary: session.selectedTone == tone) {
            session.cancel(); session.selectedTone = tone
        }.accessibilityAddTraits(session.selectedTone == tone ? .isSelected : [])
    }

    private var keyGrid: some View {
        let rows = state.symbolPage == 0 ? letters : (state.symbolPage == 1 ? numbers : symbols)
        return VStack(spacing: 6) {
            HStack(spacing: 4) { ForEach(Array(rows[0].enumerated()), id: \.offset) { _, key in characterKey(String(key)) } }
            HStack(spacing: 4) { ForEach(Array(rows[1].enumerated()), id: \.offset) { _, key in characterKey(String(key)) } }
                .padding(.horizontal, 12)
            HStack(spacing: 4) {
                Button {
                    if state.symbolPage > 0 { state.symbolPage = state.symbolPage == 1 ? 2 : 1 }
                    else { state.shifted.toggle(); state.capsLock = false }
                } label: {
                    Group {
                        if state.symbolPage > 0 { Text(state.symbolPage == 1 ? "#+=" : "123").font(.system(size: 12)) }
                        else { Image(systemName: state.capsLock ? "capslock.fill" : (state.shifted ? "shift.fill" : "shift")) }
                    }.frame(width: 36).frame(maxHeight: .infinity).background(palette.surface2).clipShape(RoundedRectangle(cornerRadius: 7))
                }
                .accessibilityLabel(state.symbolPage > 0 ? "More symbols" : "Shift; hold for caps lock")
                .onLongPressGesture { if state.symbolPage == 0 { state.capsLock.toggle(); state.shifted = state.capsLock } }
                ForEach(Array(rows[2].enumerated()), id: \.offset) { _, key in characterKey(String(key)) }
                Button(action: onDelete) {
                    Image(systemName: "delete.left").frame(width: 36).frame(maxHeight: .infinity)
                        .background(palette.surface2).clipShape(RoundedRectangle(cornerRadius: 7))
                }.accessibilityLabel("Delete")
            }
            HStack(spacing: 5) {
                Button { state.symbolPage = state.symbolPage == 0 ? 1 : 0 } label: {
                    Text(state.symbolPage == 0 ? "123" : "ABC").font(.system(size: 13, weight: .semibold))
                        .frame(width: 48).frame(maxHeight: .infinity).background(palette.surface2).clipShape(RoundedRectangle(cornerRadius: 7))
                }.accessibilityLabel(state.symbolPage == 0 ? "Numbers and symbols" : "Letters")
                Button { onInsert(" ") } label: {
                    Text("space").font(.system(size: 14)).frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(palette.accentPrimaryContainer).clipShape(RoundedRectangle(cornerRadius: 7))
                }.accessibilityLabel("Space")
                Button { onInsert("\n") } label: {
                    Image(systemName: "return").frame(width: 54).frame(maxHeight: .infinity)
                        .foregroundColor(palette.onAccentPrimary).background(palette.accentPrimary).clipShape(RoundedRectangle(cornerRadius: 7))
                }.accessibilityLabel("Return")
            }
        }.buttonStyle(.plain)
    }

    private func characterKey(_ character: String) -> some View {
        let label = state.symbolPage == 0 && (state.shifted || state.capsLock) ? character.uppercased() : character
        return Button {
            onInsert(label)
            if !state.capsLock { state.shifted = false }
        } label: {
            Text(label).font(.system(size: 20)).frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(palette.surface1).clipShape(RoundedRectangle(cornerRadius: 7))
        }.accessibilityLabel(label)
    }
}

/// UIKit's all-touch-events input-mode switcher preserves tap and hold-to-list behavior.
private struct WingGlobeButton: UIViewRepresentable {
    let tint: UIColor
    let onEvent: (UIView, UIEvent) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onEvent: onEvent) }
    func makeUIView(context: Context) -> UIButton {
        let button = UIButton(type: .system)
        button.setImage(UIImage(systemName: "globe"), for: .normal)
        button.accessibilityLabel = "Next keyboard"
        button.addTarget(context.coordinator, action: #selector(Coordinator.handle(_:event:)), for: .allTouchEvents)
        return button
    }
    func updateUIView(_ button: UIButton, context: Context) { button.tintColor = tint; context.coordinator.onEvent = onEvent }
    final class Coordinator: NSObject {
        var onEvent: (UIView, UIEvent) -> Void
        init(onEvent: @escaping (UIView, UIEvent) -> Void) { self.onEvent = onEvent }
        @objc func handle(_ sender: UIButton, event: UIEvent) { onEvent(sender, event) }
    }
}
