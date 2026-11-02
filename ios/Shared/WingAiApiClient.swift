import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public enum WingAiApiError: LocalizedError, Equatable {
    case invalidUrl
    case serverError(statusCode: Int, detail: String)
    case networkError(String)

    public var errorDescription: String? {
        switch self {
        case .invalidUrl:
            return "Enter a valid HTTPS service URL without a query, fragment or credentials."
        case let .serverError(_, detail):
            return detail
        case let .networkError(reason):
            return reason
        }
    }
}

public final class WingAiApiClient: Sendable {
    private let baseUrlProvider: @Sendable () -> String
    private let userIdProvider: @Sendable () -> String
    private let session: URLSession

    public init(
        baseUrlProvider: @escaping @Sendable () -> String = { AppGroupStore.shared.backendUrl },
        userIdProvider: @escaping @Sendable () -> String = { AppGroupStore.shared.getOrCreateUserId() },
        session: URLSession = URLSession(configuration: .ephemeral)
    ) {
        self.baseUrlProvider = baseUrlProvider
        self.userIdProvider = userIdProvider
        self.session = session
    }

    public func fetchUserProfile() async throws -> UserProfile {
        let userId = userIdProvider()
        guard var components = URLComponents(string: "\(try validatedBaseUrl())/v1/user") else {
            throw WingAiApiError.invalidUrl
        }
        components.queryItems = [URLQueryItem(name: "user_id", value: userId)]
        guard let url = components.url else {
            throw WingAiApiError.invalidUrl
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(userId, forHTTPHeaderField: "X-User-Id")
        request.timeoutInterval = 15

        return try await perform(request, decodeAs: UserProfile.self)
    }

    public func analyzeContext(
        imageBase64: String?,
        conversationText: String?,
        tone: String?,
        customInstruction: String? = nil
    ) async throws -> ReplyResponse {
        var body: [String: Any] = [
            "user_id": userIdProvider(),
            "mime_type": "image/jpeg"
        ]
        if let imageBase64, !imageBase64.isEmpty {
            body["image_base64"] = imageBase64
        }
        if let conversationText, !conversationText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            body["conversation_text"] = conversationText
        }
        if let tone, !tone.isEmpty {
            body["tone"] = tone
        }
        if let customInstruction, !customInstruction.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            body["custom_instruction"] = customInstruction
        }

        return try await postJson(path: "/v1/analyze", payload: body, decodeAs: ReplyResponse.self)
    }

    public func rewriteDraft(
        replyText: String,
        action: String,
        tone: String?,
        conversationText: String? = nil
    ) async throws -> ReplyResponse {
        var body: [String: Any] = [
            "user_id": userIdProvider(),
            "reply_text": replyText,
            "action": action
        ]
        if let tone, !tone.isEmpty {
            body["tone"] = tone
        }
        if let conversationText, !conversationText.isEmpty {
            body["conversation_text"] = conversationText
        }

        return try await postJson(path: "/v1/rewrite", payload: body, decodeAs: ReplyResponse.self)
    }

    public static func isValidBackend(_ value: String) -> Bool {
        guard let parts = URLComponents(string: value), parts.scheme?.lowercased() == "https",
              let host = parts.host, !host.isEmpty,
              parts.user == nil, parts.password == nil, parts.query == nil, parts.fragment == nil else { return false }
        return parts.url != nil
    }

    private func validatedBaseUrl() throws -> String {
        var raw = baseUrlProvider().trimmingCharacters(in: .whitespacesAndNewlines)
        while raw.hasSuffix("/") { raw.removeLast() }
        guard Self.isValidBackend(raw) else { throw WingAiApiError.invalidUrl }
        return raw
    }

    private func postJson<T: Decodable>(
        path: String,
        payload: [String: Any],
        decodeAs: T.Type
    ) async throws -> T {
        guard let url = URL(string: "\(try validatedBaseUrl())\(path)") else {
            throw WingAiApiError.invalidUrl
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(userIdProvider(), forHTTPHeaderField: "X-User-Id")
        request.timeoutInterval = 25
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        return try await perform(request, decodeAs: decodeAs)
    }

    private func perform<T: Decodable>(_ request: URLRequest, decodeAs: T.Type) async throws -> T {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            if Task.isCancelled { throw CancellationError() }
            throw WingAiApiError.networkError(error.localizedDescription)
        }

        guard let http = response as? HTTPURLResponse else {
            throw WingAiApiError.networkError("Invalid HTTP response.")
        }

        if !(200...299).contains(http.statusCode) {
            if let errJson = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let detail = errJson["detail"] as? String {
                throw WingAiApiError.serverError(statusCode: http.statusCode, detail: detail)
            }
            throw WingAiApiError.serverError(
                statusCode: http.statusCode,
                detail: "Server returned HTTP \(http.statusCode)."
            )
        }

        return try JSONDecoder().decode(T.self, from: data)
    }
}
