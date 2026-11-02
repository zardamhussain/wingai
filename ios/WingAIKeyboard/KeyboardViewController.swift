import UIKit
import SwiftUI

@MainActor
public final class KeyboardViewController: UIInputViewController {
    private let state = WingAiKeyboardViewModel()
    private let session = WingReplySession()
    private var settings: WingAiSettings!
    private var hostingController: UIHostingController<AnyView>?
    private var keyboardHeight: NSLayoutConstraint?

    public override func viewDidLoad() {
        super.viewDidLoad()
        settings = WingAiSettings()
        refreshAccess()
        let root = WingAiTheme(settings: settings) {
            WingAiKeyboardRootView(
                state: state, session: session, settings: settings,
                onInsert: { [weak self] text in self?.textDocumentProxy.insertText(text) },
                onDelete: { [weak self] in self?.textDocumentProxy.deleteBackward() },
                onGlobeEvent: { [weak self] sender, event in self?.handleInputModeList(from: sender, with: event) },
                onDismiss: { [weak self] in self?.dismissKeyboard() },
                onPaste: { [weak self] in
                    // Never read the clipboard on appearance, in the background or without permission.
                    guard let self, self.hasFullAccess else { return nil }
                    return UIPasteboard.general.string
                },
                onGenerate: { [weak self] in
                    guard let self else { return }
                    self.refreshAccess()
                    self.session.generate(settings: self.settings, networkAllowed: self.hasFullAccess)
                }
            )
        }
        let host = UIHostingController(rootView: AnyView(root))
        host.view.translatesAutoresizingMaskIntoConstraints = false
        host.view.backgroundColor = .clear
        addChild(host); view.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])
        host.didMove(toParent: self)
        hostingController = host
        let height = view.heightAnchor.constraint(equalToConstant: 330)
        height.priority = .defaultHigh
        height.isActive = true
        keyboardHeight = height
    }

    public override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        settings.reload()
        refreshAccess()
    }
    public override func viewWillDisappear(_ animated: Bool) {
        // Conversation/screenshot content is intentionally not retained between extension presentations.
        session.clear()
        super.viewWillDisappear(animated)
    }
    public override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        refreshAccess()
    }
    public override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // Keep all modes the same height; landscape is shorter and AI/themes scroll internally.
        keyboardHeight?.constant = view.bounds.width > 600 ? 270 : 330
    }
    private func refreshAccess() {
        state.hasFullAccess = hasFullAccess
        state.needsGlobe = needsInputModeSwitchKey
    }
}
