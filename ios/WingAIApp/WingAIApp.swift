import SwiftUI
import PhotosUI
import UIKit
import ImageIO

@MainActor
struct WingAiComposeScreen: View {
    @Environment(\.wingPalette) private var palette
    @ObservedObject var settings: WingAiSettings
    @ObservedObject var session: WingReplySession
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var isImporting = false
    @State private var draft = ""
    @State private var notice = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Your words.\nA little more you.").font(.largeTitle.bold())
                    Text("Choose the context. Find your tone. Make the reply yours.")
                        .foregroundColor(palette.textSecondary)
                }
                WingAiSurfaceCard {
                    Text("Start with a conversation").font(.headline)
                    TextEditor(text: $session.context)
                        .frame(minHeight: 120).scrollContentBackground(.hidden)
                        .padding(8).background(palette.surface2).clipShape(RoundedRectangle(cornerRadius: 12))
                        .accessibilityLabel("Conversation context")
                    Text("\(session.context.count) / 12,000 characters").font(.caption).foregroundColor(palette.textSecondary)
                    ViewThatFits(in: .horizontal) {
                        contextActions
                        VStack(alignment: .leading, spacing: 8) {
                            CompactPillButton(title: "Paste chat") { pasteChat() }
                            photoPicker
                            CompactPillButton(title: "Try sample") { loadSample() }
                        }
                    }
                    if isImporting { ProgressView("Preparing your image...") }
                    if let data = session.screenshotJPEG, let image = UIImage(data: data) {
                        Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 220)
                            .clipShape(RoundedRectangle(cornerRadius: 12)).accessibilityLabel("Selected screenshot, not yet uploaded")
                        Text("Check the entire image. Crop private details in Photos before selecting it.")
                            .font(.caption).foregroundColor(palette.textSecondary)
                        CompactPillButton(title: "Remove screenshot") { session.cancel(); selectedPhoto = nil; session.screenshotJPEG = nil }
                    }
                    Picker("Reply tone", selection: $session.selectedTone) {
                        Text("Mix it up").tag(Optional<WingTone>.none)
                        ForEach(WingTone.allCases, id: \.rawValue) { tone in Text(tone.displayName).tag(Optional(tone)) }
                    }.pickerStyle(.menu)
                    TextField("Optional: suggest coffee tomorrow instead", text: $session.instruction, axis: .vertical)
                        .textFieldStyle(.roundedBorder).accessibilityLabel("Optional reply instruction")
                    Text("Analyze chat sends the context shown above to your selected service.")
                        .font(.caption).foregroundColor(palette.textSecondary)
                    if session.isLoading {
                        HStack { ProgressView(); CompactPillButton(title: "Cancel") { session.cancel() } }
                    } else {
                        CompactPillButton(title: "Analyze chat", isPrimary: true,
                                          isDisabled: !session.hasContext || !settings.hasServiceConsent || isImporting) {
                            session.generate(settings: settings)
                        }
                    }
                }
                if !settings.hasServiceConsent { WingAiSurfaceCard { WingServiceConsentView(settings: settings) } }
                if let error = session.error {
                    Text(error).font(.subheadline).foregroundColor(palette.accentRose).accessibilityLabel("Notice: \(error)")
                }
                if !session.replies.isEmpty {
                    Text(session.isSample ? "Sample replies - no AI request" : "Your reply, your way").font(.headline)
                    ForEach(Array(session.replies.enumerated()), id: \.offset) { _, reply in
                        WingAiSurfaceCard {
                            Text(reply.tone.capitalized).font(.caption.weight(.semibold)).foregroundColor(palette.accentPrimary)
                            Text(reply.text).textSelection(.enabled)
                            CompactPillButton(title: "Use this reply", isPrimary: true) { draft = reply.text }
                        }
                    }
                }
                WingAiSurfaceCard {
                    Text("Make it sound like you").font(.headline)
                    TextEditor(text: $draft).frame(minHeight: 100).scrollContentBackground(.hidden)
                        .accessibilityLabel("Editable reply draft")
                    CompactPillButton(title: "Copy reply", isPrimary: true, isDisabled: draft.isEmpty) {
                        UIPasteboard.general.setItems([["public.utf8-plain-text": draft]], options: [.localOnly: true])
                        notice = "Copied. Switch to your conversation and paste when you're ready."
                    }
                    Text("This app never sends a message for you.").font(.caption).foregroundColor(palette.textSecondary)
                }
                if !notice.isEmpty { Text(notice).font(.caption).foregroundColor(palette.textSecondary) }
                CompactPillButton(title: "Clear this conversation") {
                    selectedPhoto = nil; session.clear(); draft = ""; notice = ""
                }
            }.padding(20)
        }
        .background(palette.bgCanvas.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: session.context) { _ in session.cancel() }
        .onChange(of: session.instruction) { _ in session.cancel() }
        .onChange(of: session.selectedTone) { _ in session.cancel() }
        .task(id: selectedPhoto) { await importPhoto() }
    }

    private var contextActions: some View {
        HStack(spacing: 8) {
            CompactPillButton(title: "Paste chat") { pasteChat() }
            photoPicker
            CompactPillButton(title: "Try sample") { loadSample() }
        }
    }
    private var photoPicker: some View {
        PhotosPicker(selection: $selectedPhoto, matching: .images, photoLibrary: .shared()) {
            Label("Screenshot", systemImage: "photo").font(.subheadline.weight(.semibold))
                .padding(.horizontal, 12).frame(minHeight: 44)
                .background(palette.surface2).clipShape(Capsule())
        }.buttonStyle(.plain)
    }
    private func pasteChat() {
        session.cancel()
        if let value = UIPasteboard.general.string, !value.isEmpty { session.context = value }
        else { notice = "No text found on the clipboard." }
    }
    private func loadSample() {
        selectedPhoto = nil; session.loadSample(); notice = "Prewritten sample replies. No data was uploaded."
    }
    private func importPhoto() async {
        guard let selection = selectedPhoto else { isImporting = false; return }
        session.cancel(); isImporting = true
        do {
            guard let data = try await selection.loadTransferable(type: Data.self) else { throw ImageImportError.unreadable }
            try Task.checkCancellation()
            let jpeg = try await Task.detached(priority: .userInitiated) { try prepareScreenshot(data) }.value
            try Task.checkCancellation()
            session.screenshotJPEG = jpeg
            notice = "Image loaded locally. It uploads only when you tap Analyze chat."
            isImporting = false
        } catch {
            guard !Task.isCancelled else { return }
            isImporting = false
            notice = error.localizedDescription
        }
    }
}

private enum ImageImportError: LocalizedError {
    case unreadable
    var errorDescription: String? { "Choose an image under 20 MB. Crop it in Photos if necessary." }
}

/// Downsample with orientation before JPEG encoding; don't decode full-resolution photos in the keyboard.
private func prepareScreenshot(_ data: Data) throws -> Data {
    guard data.count <= 20_000_000,
          let source = CGImageSourceCreateWithData(data as CFData, nil),
          let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: 1600
          ] as CFDictionary),
          let jpeg = UIImage(cgImage: image).jpegData(compressionQuality: 0.78), jpeg.count <= 2_000_000 else {
        throw ImageImportError.unreadable
    }
    return jpeg
}

@MainActor
struct WingAiAccountScreen: View {
    @Environment(\.wingPalette) private var palette
    @ObservedObject var settings: WingAiSettings
    @ObservedObject var session: WingReplySession
    @State private var endpoint = ""
    @State private var notice = ""
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                WingAiThemePicker(settings: settings)
                Text(settings.usesSharedGroup ? "Settings use the configured App Group. Reopen the keyboard to refresh." : "App and keyboard settings are independent in this build. Pick a keyboard theme from its palette button.")
                    .font(.caption).foregroundColor(palette.textSecondary)
                WingAiSurfaceCard {
                    Text("A little help, every day.").font(.title2.bold())
                    if let user = session.profile {
                        Text(user.isPaid ? "WingAI Pro - Unlimited requests" : "WingAI Free").font(.headline)
                        if !user.isPaid {
                            let limit = max(0, user.dailyLimit ?? 5)
                            let left = min(limit, max(0, user.remaining ?? (limit - user.dailyUsed)))
                            Text("\(left) of \(limit) requests left today")
                            ProgressView(value: Double(max(0, min(user.dailyUsed, limit))), total: Double(max(1, limit)))
                            Text("Resets at 00:00 UTC").font(.caption).foregroundColor(palette.textSecondary)
                        }
                    } else {
                        Text("Connect to check your plan. No usage numbers are assumed.").font(.subheadline).foregroundColor(palette.textSecondary)
                    }
                    CompactPillButton(title: session.isLoading ? "Refreshing..." : "Refresh usage",
                                      isDisabled: session.isLoading || !settings.hasServiceConsent) { session.refreshProfile(settings: settings) }
                }
                if let error = session.error { Text(error).font(.subheadline).foregroundColor(palette.accentRose) }
                WingAiSurfaceCard {
                    WingServiceConsentView(settings: settings)
                    DisclosureGroup("Change AI service") {
                        TextField("https://your-service.example", text: $endpoint)
                            .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                            .textFieldStyle(.roundedBorder)
                        CompactPillButton(title: "Save service") {
                            notice = settings.saveBackend(endpoint) ? "Saved. Review consent for the new service." : "Enter a valid HTTPS URL without credentials, a query or a fragment."
                        }
                    }
                }
                WingAiSurfaceCard {
                    Text("Device user ID").font(.headline)
                    Text(settings.userID).font(.caption.monospaced()).textSelection(.enabled)
                    CompactPillButton(title: "Copy ID") {
                        UIPasteboard.general.setItems([["public.utf8-plain-text": settings.userID]], options: [.localOnly: true])
                        notice = "Device ID copied."
                    }
                }
                if !notice.isEmpty { Text(notice).font(.caption).foregroundColor(palette.textSecondary) }
            }.padding(20)
        }.background(palette.bgCanvas.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { endpoint = settings.backendURL }
    }
}

struct WingAiSetupScreen: View {
    @Environment(\.wingPalette) private var palette
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Image(systemName: "sparkles").font(.largeTitle).foregroundColor(palette.accentPrimary)
                Text("Good conversations\nstart here.").font(.largeTitle.bold())
                Text("Use the app on its own, or add WingAI as a keyboard.").foregroundColor(palette.textSecondary)
                step("1", "Add your keyboard", "In iPhone Settings, go to General > Keyboard > Keyboards > Add New Keyboard, then choose WingAI.")
                step("2", "Switch to WingAI", "In a text field, touch and hold the globe key, then choose WingAI. Its globe button takes you back to another keyboard.")
                step("3", "Optional: enable online replies", "In Settings > General > Keyboard > Keyboards > WingAI, enable Allow Full Access. Then review the AI service in WingAI's reply panel. Full Access alone does not send your text.")
                WingAiSurfaceCard {
                    Text("Made for iPhone, not an Android copy.").font(.headline)
                    Text("A keyboard can't read another app's screen. Copy the chat, paste it into WingAI, review it and tap Generate. For screenshots, use the WingAI app's photo picker.")
                    Text("iOS uses Apple's keyboard for passwords and some phone-number fields. Some apps block custom keyboards entirely.")
                    Text("This first version has English keys, numbers, symbols and a globe switcher. HeliBoard's swipe typing, autocorrect, voice input and dictionaries have not been ported.")
                }
                CompactPillButton(title: "Open app settings", isPrimary: true) {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                }
                Text("The button opens this app's settings, not a private keyboard-settings shortcut. Follow the manual path above if needed. Setup permissions are not automatically marked as complete.")
                    .font(.caption).foregroundColor(palette.textSecondary)
            }.padding(20)
        }.background(palette.bgCanvas.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
    }
    private func step(_ number: String, _ title: String, _ detail: String) -> some View {
        WingAiSurfaceCard {
            HStack {
                Text(number).font(.headline).foregroundColor(palette.accentPrimary)
                Text(title).font(.headline)
            }
            Text(detail).font(.subheadline).foregroundColor(palette.textSecondary)
        }
    }
}
