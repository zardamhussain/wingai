// swift-tools-version: 5.9
import PackageDescription

// Foundation-only model/API tests. The installable app/extension are in WingAI.xcodeproj.
let package = Package(
    name: "WingAIiOS",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "WingAICore", targets: ["WingAICore"])],
    targets: [
        .target(
            name: "WingAICore", path: "Shared",
            exclude: ["WingAiDesignSystem.swift", "WingAiSettings.swift", "WingReplySession.swift"],
            sources: ["WingModels.swift", "WingAppearance.swift", "WingAiApiClient.swift"]
        ),
        .testTarget(name: "WingAITests", dependencies: ["WingAICore"], path: "Tests")
    ]
)
