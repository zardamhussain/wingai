import XCTest
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
#if SWIFT_PACKAGE
@testable import WingAICore
#else
@testable import WingAI
#endif

final class WingAITests: XCTestCase {
    func testAppearanceIDsAndUnknownFallback() {
        XCTAssertEqual(WingAppearance.allCases.count, 5)
        XCTAssertEqual(WingAppearance.from(nil), .system)
        XCTAssertEqual(WingAppearance.from("future-theme"), .system)
        for theme in WingAppearance.allCases { XCTAssertEqual(WingAppearance.from(theme.rawValue), theme) }
    }

    func testLocalSettingsPersistAndConsentStartsOff() throws {
        let suite = "wingai.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let first = AppGroupStore(defaults: defaults)
        XCTAssertNil(first.consentedBackend)
        XCTAssertFalse(first.usesSharedGroup)
        let id = first.getOrCreateUserId()
        XCTAssertTrue(id.hasPrefix("wing_"))
        first.appearance = .rose
        first.consentedBackend = first.backendUrl
        let second = AppGroupStore(defaults: defaults)
        XCTAssertEqual(second.getOrCreateUserId(), id)
        XCTAssertEqual(second.appearance, .rose)
        XCTAssertEqual(second.consentedBackend, second.backendUrl)
    }

    func testEndpointValidationRequiresHTTPSAndNoCredentials() {
        XCTAssertTrue(WingAiApiClient.isValidBackend("https://example.com"))
        XCTAssertTrue(WingAiApiClient.isValidBackend("https://example.com/api"))
        for invalid in ["http://example.com", "file:///tmp/file", "https://user:pass@example.com", "https://example.com?q=x", "https://example.com#fragment", "not a URL"] {
            XCTAssertFalse(WingAiApiClient.isValidBackend(invalid), invalid)
        }
    }

    func testAPIContractDecoding() throws {
        let data = Data("""
        {"status":"ok","context_status":"sufficient","message":null,
         "replies":[{"tone":"friendly","text":"See you at 4!"}],
         "user":{"user_id":"wing_test","tier":"free","daily_used":2,"daily_limit":5,"remaining":3,"usage_date":"2026-11-01"}}
        """.utf8)
        let response = try JSONDecoder().decode(ReplyResponse.self, from: data)
        XCTAssertEqual(response.replies.first?.text, "See you at 4!")
        XCTAssertEqual(response.user?.remaining, 3)
        XCTAssertEqual(response.user?.isPaid, false)
    }

    func testInvalidURLRejectedWithoutNetwork() async {
        let client = WingAiApiClient(baseUrlProvider: { "http://example.com" }, userIdProvider: { "wing_test" })
        do {
            _ = try await client.fetchUserProfile()
            XCTFail("HTTP must be rejected")
        } catch {
            XCTAssertEqual(error as? WingAiApiError, .invalidUrl)
        }
    }
}
