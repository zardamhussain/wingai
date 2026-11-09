import { describe, it, expect, beforeEach } from "vitest";
import app, { Env, resetRateLimiter, setGeminiFetcher, setTestUserTier } from "../src/index";

const DEFAULT_ENV: Env = {
  GEMINI_API_KEY: "test-api-key",
  GEMINI_MODEL: "gemini-3.8-flash",
  MAX_REQUEST_BYTES: "5242880",
  RATE_LIMIT_PER_MINUTE: "30",
  FREE_DAILY_LIMIT: "5",
};

function mockGeminiSuccess(customReplies?: Array<{ tone: string; text: string }>) {
  const payload = {
    status: "success",
    context_status: "sufficient",
    replies: customReplies ?? [
      { tone: "casual", text: "Sure, that works for me!" },
      { tone: "friendly", text: "Sounds great! Looking forward to it." },
      { tone: "professional", text: "That works well for me. Thank you." },
    ],
  };

  setGeminiFetcher(async () => {
    return new Response(
      JSON.stringify({
        candidates: [
          {
            content: {
              parts: [{ text: JSON.stringify(payload) }],
            },
          },
        ],
      }),
      { status: 200, headers: { "Content-Type": "application/json" } }
    );
  });
}

describe("WingAI Cloudflare Worker API", () => {
  beforeEach(() => {
    resetRateLimiter();
    mockGeminiSuccess();
  });

  it("GET /v1/health returns ok", async () => {
    const res = await app.request("/v1/health", {}, DEFAULT_ENV);
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toEqual({ status: "ok", service: "wingai-backend" });
  });

  it("POST /v1/analyze succeeds with image_base64 and custom_instruction", async () => {
    const imgB64 = btoa("fakejpegbytes");
    const res = await app.request(
      "/v1/analyze",
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-User-Id": "user_test_1" },
        body: JSON.stringify({
          image_base64: imgB64,
          mime_type: "image/jpeg",
          tone: "friendly",
          custom_instruction: "Tell them I am busy today. Suggest tomorrow.",
        }),
      },
      DEFAULT_ENV
    );
    expect(res.status).toBe(200);
    const body: any = await res.json();
    expect(body.status).toBe("success");
    expect(body.context_status).toBe("sufficient");
    expect(body.replies).toHaveLength(3);
    expect(body.user.daily_used).toBe(1);
    expect(body.user.remaining).toBe(4);
  });

  it("enforces 5 requests per day for free users and allows unlimited for paid users", async () => {
    const headers = {
      "Content-Type": "application/json",
      "X-User-Id": "free_user_123",
    };

    for (let i = 1; i <= 5; i++) {
      const res = await app.request(
        "/v1/replies",
        {
          method: "POST",
          headers,
          body: JSON.stringify({ conversation_text: `Message ${i}` }),
        },
        DEFAULT_ENV
      );
      expect(res.status).toBe(200);
    }

    // 6th request as free user should be blocked with 429
    const blocked = await app.request(
      "/v1/replies",
      {
        method: "POST",
        headers,
        body: JSON.stringify({ conversation_text: "Message 6" }),
      },
      DEFAULT_ENV
    );
    expect(blocked.status).toBe(429);

    // Manually upgrade user to paid in storage
    const upgradedProfile = await setTestUserTier(DEFAULT_ENV, "free_user_123", "paid");
    expect(upgradedProfile.tier).toBe("paid");
    expect(upgradedProfile.daily_limit).toBeNull();

    // 6th request now succeeds because user is paid
    const allowed = await app.request(
      "/v1/replies",
      {
        method: "POST",
        headers,
        body: JSON.stringify({ conversation_text: "Message 6 after upgrade" }),
      },
      DEFAULT_ENV
    );
    expect(allowed.status).toBe(200);
  });

  it("POST /v1/analyze rejects empty context with 422", async () => {
    const res = await app.request(
      "/v1/analyze",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      },
      DEFAULT_ENV
    );
    expect(res.status).toBe(422);
  });

  it("POST /v1/rewrite rewrites draft reply", async () => {
    const res = await app.request(
      "/v1/rewrite",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          reply_text: "Sure, I can meet tomorrow afternoon.",
          action: "shorten",
          tone: "short",
        }),
      },
      DEFAULT_ENV
    );
    expect(res.status).toBe(200);
    const body: any = await res.json();
    expect(body.replies).toHaveLength(3);
  });

  it("returns 503 when upstream Gemini fails", async () => {
    setGeminiFetcher(async () => new Response("Internal error", { status: 500 }));
    const res = await app.request(
      "/v1/replies",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ conversation_text: "Hello there!" }),
      },
      DEFAULT_ENV
    );
    expect(res.status).toBe(503);
  });

  it("returns 503 when GEMINI_API_KEY is missing", async () => {
    const res = await app.request(
      "/v1/replies",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ conversation_text: "Hello there!" }),
      },
      { ...DEFAULT_ENV, GEMINI_API_KEY: "" }
    );
    expect(res.status).toBe(503);
  });

  it("enforces rate limit per client IP", async () => {
    const envWithLowLimit: Env = { ...DEFAULT_ENV, RATE_LIMIT_PER_MINUTE: "2" };
    const reqInit = {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "CF-Connecting-IP": "203.0.113.5",
      },
      body: JSON.stringify({ conversation_text: "Hi" }),
    };

    const r1 = await app.request("/v1/replies", reqInit, envWithLowLimit);
    const r2 = await app.request("/v1/replies", reqInit, envWithLowLimit);
    const r3 = await app.request("/v1/replies", reqInit, envWithLowLimit);

    expect(r1.status).toBe(200);
    expect(r2.status).toBe(200);
    expect(r3.status).toBe(429);
  });

  it("saves cropped image and JSON audit log to R2 STORAGE when bound", async () => {
    mockGeminiSuccess();
    const storedObjects = new Map<string, { value: Uint8Array | string; contentType?: string }>();
    const mockR2 = {
      async put(
        key: string,
        value: Uint8Array | string,
        options?: { httpMetadata?: { contentType?: string } }
      ) {
        storedObjects.set(key, {
          value,
          contentType: options?.httpMetadata?.contentType,
        });
      },
    };

    const res = await app.request(
      "/v1/analyze",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-User-Id": "wing_r2_user",
        },
        body: JSON.stringify({
          image_base64: btoa("fake-jpeg-binary"),
          mime_type: "image/jpeg",
          tone: "casual",
        }),
      },
      { ...DEFAULT_ENV, STORAGE: mockR2 }
    );
    expect(res.status).toBe(200);

    // Wait microtask tick for async archival
    await new Promise((r) => setTimeout(r, 15));

    const keys = Array.from(storedObjects.keys());
    const imageKey = keys.find((k) => k.startsWith("images/") && k.endsWith(".jpg"));
    const logKey = keys.find((k) => k.startsWith("logs/") && k.endsWith(".json"));
    expect(imageKey).toBeDefined();
    expect(logKey).toBeDefined();

    const logContent = JSON.parse(String(storedObjects.get(logKey!)?.value));
    expect(logContent.user_id).toBe("wing_r2_user");
    expect(logContent.image_r2_key).toBe(imageKey);
    expect(logContent.status_code).toBe(200);
  });

  it("POST /v1/analyze accepts systematic structured_context from on-device OCR without image_base64", async () => {
    let capturedPrompt = "";
    setGeminiFetcher(async (_url, init) => {
      const reqJson = JSON.parse(String(init?.body || "{}"));
      capturedPrompt = reqJson?.contents?.[0]?.parts?.[0]?.text || "";
      return new Response(
        JSON.stringify({
          candidates: [
            {
              content: {
                parts: [
                  {
                    text: JSON.stringify({
                      status: "success",
                      context_status: "sufficient",
                      replies: [
                        { tone: "casual", text: "Haan 7 PM pakka milte hain!" },
                        { tone: "friendly", text: "Sounds good, 7 PM at Blue Tokai works!" },
                        { tone: "professional", text: "7:00 PM works well for me. See you there." },
                      ],
                    }),
                  },
                ],
              },
            },
          ],
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    });

    const res = await app.request(
      "/v1/analyze",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-User-Id": "wing_ocr_user",
        },
        body: JSON.stringify({
          tone: "casual",
          structured_context: {
            source: "ocr_select_and_search",
            language_hint: "hinglish",
            latest_incoming_message: "Blue Tokai chalte hain 7 PM?",
            messages: [
              { index: 1, sender: "them", text: "Hey kal sham ko milte hain?", alignment: "left" },
              { index: 2, sender: "me", text: "Haan kahan milna hai?", alignment: "right" },
              { index: 3, sender: "them", text: "Blue Tokai chalte hain 7 PM?", alignment: "left" },
            ],
          },
        }),
      },
      DEFAULT_ENV
    );

    expect(res.status).toBe(200);
    const body: any = await res.json();
    expect(body.status).toBe("success");
    expect(body.replies).toHaveLength(3);
    expect(capturedPrompt).toContain("=== PRE-ANALYZED CONVERSATION SPEC ===");
    expect(capturedPrompt).toContain("Language Hint: hinglish");
    expect(capturedPrompt).toContain("[PARTNER (Them)]: Blue Tokai chalte hain 7 PM?");
    expect(capturedPrompt).toContain("[USER (Me)]: Haan kahan milna hai?");
  });
});
