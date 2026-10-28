import { Hono, Context, Next } from "hono";
import { cors } from "hono/cors";

export interface D1Binding {
  prepare(query: string): {
    bind(...values: unknown[]): {
      first<T = Record<string, unknown>>(): Promise<T | null>;
      run(): Promise<unknown>;
    };
  };
}

export interface Env {
  GEMINI_API_KEY?: string;
  GEMINI_MODEL?: string;
  MAX_REQUEST_BYTES?: string;
  RATE_LIMIT_PER_MINUTE?: string;
  FREE_DAILY_LIMIT?: string;
  DB?: D1Binding;
}

export type UserTier = "free" | "paid";

export interface UserProfile {
  user_id: string;
  tier: UserTier;
  daily_used: number;
  daily_limit: number | null;
  remaining: number | null;
  usage_date: string;
}

export type AllowedTone =
  | "casual"
  | "friendly"
  | "professional"
  | "funny"
  | "flirty"
  | "short"
  | "detailed";

export type RewriteAction = "rewrite" | "shorten" | "expand" | "tone";

export interface ReplyItem {
  tone: string;
  text: string;
}

export interface ReplyResponse {
  status: "success" | "error";
  context_status: "sufficient" | "insufficient";
  message?: string | null;
  replies: ReplyItem[];
  user?: UserProfile;
}

const ALLOWED_TONES = new Set<string>([
  "casual",
  "friendly",
  "professional",
  "funny",
  "flirty",
  "short",
  "detailed",
]);

const ALLOWED_ACTIONS = new Set<string>(["rewrite", "shorten", "expand", "tone"]);
const DEFAULT_TONES = ["casual", "friendly", "professional"] as const;

export const SYSTEM_PROMPT = `You are WingAI, an assistant that helps the user write replies to messages.

Follow these rules:
1. Identify the available conversation text from the image or text input.
2. Identify the sender of each message when possible.
3. Identify the latest message that needs a reply.
4. Generate exactly three reply options.
5. When no specific tone is requested, use these three tones in order: "casual", "friendly", "professional".
6. When a specific tone is requested, generate three variations in that requested tone.
7. When a custom instruction is provided, follow the instruction in all three replies.
8. Do not invent facts about the user.
9. Do not assume that the user agreed to a request unless the custom instruction says so.
10. If the conversation is not clear or readable, set context_status to "insufficient" and provide neutral replies or ask for more context.
11. Return valid JSON matching the required schema.`;

const REPLY_RESPONSE_SCHEMA = {
  type: "OBJECT",
  properties: {
    status: {
      type: "STRING",
      enum: ["success", "error"],
    },
    context_status: {
      type: "STRING",
      enum: ["sufficient", "insufficient"],
    },
    message: {
      type: "STRING",
      nullable: true,
    },
    replies: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          tone: { type: "STRING" },
          text: { type: "STRING" },
        },
        required: ["tone", "text"],
      },
    },
  },
  required: ["status", "context_status", "replies"],
};

// Isolate-scoped rate limiter (sliding 60-second window per client IP)
const rateLimitBuckets = new Map<string, number[]>();

// In-memory fallback store for unit tests when D1 is not bound
const memoryUsers = new Map<
  string,
  { user_id: string; tier: UserTier; daily_used: number; usage_date: string }
>();

export function resetRateLimiter(): void {
  rateLimitBuckets.clear();
  memoryUsers.clear();
}

function todayUtc(): string {
  return new Date().toISOString().slice(0, 10);
}

function getFreeDailyLimit(env: Env): number {
  return parseInt(env.FREE_DAILY_LIMIT || "5", 10);
}

function toUserProfile(
  row: { user_id: string; tier: UserTier; daily_used: number; usage_date: string },
  env: Env
): UserProfile {
  const limit = getFreeDailyLimit(env);
  const isPaid = row.tier === "paid";
  return {
    user_id: row.user_id,
    tier: isPaid ? "paid" : "free",
    daily_used: row.daily_used,
    daily_limit: isPaid ? null : limit,
    remaining: isPaid ? null : Math.max(0, limit - row.daily_used),
    usage_date: row.usage_date,
  };
}

async function getOrInitUser(env: Env, userId: string): Promise<UserProfile> {
  const today = todayUtc();
  if (env.DB) {
    const existing = await env.DB.prepare(
      "SELECT user_id, tier, daily_used, usage_date FROM users WHERE user_id = ?"
    )
      .bind(userId)
      .first<{ user_id: string; tier: string; daily_used: number; usage_date: string }>();

    if (!existing) {
      await env.DB.prepare(
        "INSERT INTO users (user_id, tier, daily_used, usage_date) VALUES (?, 'free', 0, ?)"
      )
        .bind(userId, today)
        .run();
      return toUserProfile(
        { user_id: userId, tier: "free", daily_used: 0, usage_date: today },
        env
      );
    }

    const tier: UserTier = existing.tier === "paid" ? "paid" : "free";
    let dailyUsed = Number(existing.daily_used) || 0;
    if (existing.usage_date !== today) {
      dailyUsed = 0;
      await env.DB.prepare(
        "UPDATE users SET daily_used = 0, usage_date = ? WHERE user_id = ?"
      )
        .bind(today, userId)
        .run();
    }
    return toUserProfile(
      { user_id: userId, tier, daily_used: dailyUsed, usage_date: today },
      env
    );
  }

  let rec = memoryUsers.get(userId);
  if (!rec) {
    rec = { user_id: userId, tier: "free", daily_used: 0, usage_date: today };
    memoryUsers.set(userId, rec);
  } else if (rec.usage_date !== today) {
    rec.daily_used = 0;
    rec.usage_date = today;
  }
  return toUserProfile(rec, env);
}

async function incrementUserUsage(env: Env, userId: string): Promise<UserProfile> {
  const current = await getOrInitUser(env, userId);
  const nextUsed = current.daily_used + 1;
  const today = todayUtc();

  if (env.DB) {
    await env.DB.prepare(
      "UPDATE users SET daily_used = ?, usage_date = ? WHERE user_id = ?"
    )
      .bind(nextUsed, today, userId)
      .run();
  } else {
    const rec = memoryUsers.get(userId);
    if (rec) {
      rec.daily_used = nextUsed;
      rec.usage_date = today;
    }
  }
  return toUserProfile(
    { user_id: userId, tier: current.tier, daily_used: nextUsed, usage_date: today },
    env
  );
}

export async function setTestUserTier(
  env: Env,
  userId: string,
  tier: UserTier
): Promise<UserProfile> {
  const current = await getOrInitUser(env, userId);
  if (env.DB) {
    await env.DB.prepare("UPDATE users SET tier = ? WHERE user_id = ?")
      .bind(tier, userId)
      .run();
  } else {
    const rec = memoryUsers.get(userId);
    if (rec) {
      rec.tier = tier;
    }
  }
  return toUserProfile(
    {
      user_id: userId,
      tier,
      daily_used: current.daily_used,
      usage_date: current.usage_date,
    },
    env
  );
}

function resolveUserId(c: Context<{ Bindings: Env }>, payload?: Record<string, unknown>): string {
  const fromHeader = c.req.header("x-user-id")?.trim();
  if (fromHeader) return fromHeader;
  const fromQuery = c.req.query("user_id")?.trim();
  if (fromQuery) return fromQuery;
  if (payload && typeof payload.user_id === "string" && payload.user_id.trim()) {
    return payload.user_id.trim();
  }
  return (
    c.req.header("cf-connecting-ip") ||
    c.req.header("x-forwarded-for")?.split(",")[0]?.trim() ||
    "anonymous"
  );
}

function checkRateLimit(clientKey: string, limitPerMinute: number): boolean {
  const now = Date.now();
  const windowStart = now - 60_000;
  const bucket = rateLimitBuckets.get(clientKey) ?? [];
  while (bucket.length > 0 && bucket[0] < windowStart) {
    bucket.shift();
  }
  if (bucket.length >= limitPerMinute) {
    rateLimitBuckets.set(clientKey, bucket);
    return false;
  }
  bucket.push(now);
  rateLimitBuckets.set(clientKey, bucket);
  return true;
}

function isValidBase64(input: string): boolean {
  const trimmed = input.trim();
  if (!trimmed || trimmed.length % 4 !== 0) {
    return false;
  }
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(trimmed)) {
    return false;
  }
  try {
    atob(trimmed);
    return true;
  } catch {
    return false;
  }
}

export type GeminiFetcher = typeof fetch;

let customFetcher: GeminiFetcher | null = null;

export function setGeminiFetcher(fetcher: GeminiFetcher | null): void {
  customFetcher = fetcher;
}

async function callGemini(
  parts: Array<Record<string, unknown>>,
  env: Env,
  requestedTone?: string | null
): Promise<{ status: number; body: ReplyResponse | { detail: string } }> {
  const apiKey = env.GEMINI_API_KEY?.trim() ?? "";
  if (!apiKey) {
    return {
      status: 503,
      body: { detail: "AI service is not configured. Set GEMINI_API_KEY on the backend." },
    };
  }

  const model = env.GEMINI_MODEL?.trim() || "gemini-3.8-flash";
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(
    model
  )}:generateContent?key=${encodeURIComponent(apiKey)}`;

  const requestBody = {
    systemInstruction: {
      parts: [{ text: SYSTEM_PROMPT }],
    },
    contents: [
      {
        role: "user",
        parts,
      },
    ],
    generationConfig: {
      responseMimeType: "application/json",
      responseSchema: REPLY_RESPONSE_SCHEMA,
      temperature: 0.4,
    },
  };

  const fetchFn = customFetcher ?? fetch;
  let upstreamResponse: Response;
  try {
    upstreamResponse = await fetchFn(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(requestBody),
    });
  } catch {
    return {
      status: 503,
      body: { detail: "AI service failed to process the request." },
    };
  }

  if (!upstreamResponse.ok) {
    return {
      status: 503,
      body: { detail: "AI service failed to process the request." },
    };
  }

  let upstreamJson: any;
  try {
    upstreamJson = await upstreamResponse.json();
  } catch {
    return {
      status: 503,
      body: { detail: "AI service returned an invalid response format." },
    };
  }

  const rawText: string | undefined =
    upstreamJson?.candidates?.[0]?.content?.parts?.[0]?.text;

  if (!rawText || !rawText.trim()) {
    return {
      status: 503,
      body: { detail: "AI service returned an empty response." },
    };
  }

  let parsed: Partial<ReplyResponse>;
  try {
    parsed = JSON.parse(rawText);
  } catch {
    return {
      status: 503,
      body: { detail: "AI service returned an invalid response format." },
    };
  }

  if (!parsed || !Array.isArray(parsed.replies) || parsed.replies.length === 0) {
    return {
      status: 503,
      body: { detail: "AI service returned no reply suggestions." },
    };
  }

  const normalizedReplies: ReplyItem[] = [];
  for (let idx = 0; idx < Math.min(parsed.replies.length, 3); idx++) {
    const item = parsed.replies[idx];
    if (!item || typeof item.text !== "string") {
      return {
        status: 503,
        body: { detail: "AI service returned an invalid response format." },
      };
    }
    const fallbackTone = requestedTone || DEFAULT_TONES[idx % DEFAULT_TONES.length];
    normalizedReplies.push({
      tone: (item.tone || fallbackTone).toLowerCase(),
      text: item.text.trim(),
    });
  }

  while (normalizedReplies.length < 3) {
    const fallbackTone =
      requestedTone || DEFAULT_TONES[normalizedReplies.length % DEFAULT_TONES.length];
    normalizedReplies.push({
      tone: fallbackTone,
      text: normalizedReplies[normalizedReplies.length - 1].text,
    });
  }

  return {
    status: 200,
    body: {
      status: "success",
      context_status:
        parsed.context_status === "insufficient" ? "insufficient" : "sufficient",
      message: parsed.message ?? null,
      replies: normalizedReplies,
    },
  };
}

const app = new Hono<{ Bindings: Env }>();

app.use("*", cors());

async function enforceRequestGuards(c: Context<{ Bindings: Env }>, next: Next) {
  const maxBytes = parseInt(c.env?.MAX_REQUEST_BYTES || "5242880", 10);
  const rateLimit = parseInt(c.env?.RATE_LIMIT_PER_MINUTE || "30", 10);

  const contentLength = c.req.header("content-length");
  if (contentLength && parseInt(contentLength, 10) > maxBytes) {
    return c.json({ detail: "Request payload is too large." }, 413);
  }

  const rawBody = await c.req.raw.clone().arrayBuffer();
  if (rawBody.byteLength > maxBytes) {
    return c.json({ detail: "Request payload is too large." }, 413);
  }

  const clientIp =
    c.req.header("cf-connecting-ip") ||
    c.req.header("x-forwarded-for")?.split(",")[0]?.trim() ||
    "anonymous";

  if (!checkRateLimit(clientIp, rateLimit)) {
    return c.json({ detail: "Rate limit exceeded. Try again in a moment." }, 429);
  }

  await next();
}

app.get("/v1/health", (c) => {
  return c.json({ status: "ok", service: "wingai-backend" });
});

app.get("/v1/user", async (c) => {
  const userId = resolveUserId(c);
  const profile = await getOrInitUser(c.env ?? {}, userId);
  return c.json(profile);
});

app.post("/v1/analyze", enforceRequestGuards, async (c) => {
  let payload: Record<string, unknown>;
  try {
    payload = await c.req.json();
  } catch {
    return c.json({ detail: "Invalid JSON body." }, 422);
  }

  const imageBase64 =
    typeof payload.image_base64 === "string" ? payload.image_base64 : undefined;
  const mimeType =
    typeof payload.mime_type === "string" && payload.mime_type.trim()
      ? payload.mime_type.trim()
      : "image/jpeg";
  const conversationText =
    typeof payload.conversation_text === "string" ? payload.conversation_text : undefined;
  const tone = typeof payload.tone === "string" ? payload.tone : undefined;
  const customInstruction =
    typeof payload.custom_instruction === "string"
      ? payload.custom_instruction
      : undefined;

  if (imageBase64 && imageBase64.length > 6_000_000) {
    return c.json({ detail: "image_base64 exceeds maximum length." }, 422);
  }
  if (conversationText && conversationText.length > 12_000) {
    return c.json({ detail: "conversation_text exceeds maximum length." }, 422);
  }
  if (customInstruction && customInstruction.length > 500) {
    return c.json({ detail: "custom_instruction exceeds maximum length." }, 422);
  }
  if (tone && !ALLOWED_TONES.has(tone)) {
    return c.json({ detail: `Invalid tone: ${tone}` }, 422);
  }

  const hasImage = Boolean(imageBase64 && imageBase64.trim());
  const hasText = Boolean(conversationText && conversationText.trim());
  if (!hasImage && !hasText) {
    return c.json({ detail: "Provide image_base64 or conversation_text." }, 422);
  }

  const parts: Array<Record<string, unknown>> = [];
  if (hasImage && imageBase64) {
    if (!isValidBase64(imageBase64)) {
      return c.json({ detail: "Invalid base64 image data." }, 400);
    }
    parts.push({
      inlineData: {
        mimeType,
        data: imageBase64.trim(),
      },
    });
  }

  const userId = resolveUserId(c, payload);
  const userProfile = await getOrInitUser(c.env ?? {}, userId);
  if (
    userProfile.tier === "free" &&
    userProfile.remaining !== null &&
    userProfile.remaining <= 0
  ) {
    return c.json(
      {
        detail: `Daily free limit reached (${userProfile.daily_used}/${userProfile.daily_limit} used today). Upgrade to Paid for unlimited replies.`,
        user: userProfile,
      },
      429
    );
  }

  const promptLines = [
    "Analyze the visible conversation context and generate exactly 3 reply suggestions.",
    "Identify the sender of each message and focus on the latest incoming message.",
    "Do not invent facts about the user and do not assume agreement unless instructed.",
  ];
  if (hasText && conversationText) {
    promptLines.push(`Conversation text:\n${conversationText.trim()}`);
  }
  if (tone) {
    promptLines.push(`Use this tone for all 3 replies: ${tone}.`);
  } else {
    promptLines.push("Use these 3 tones in order: casual, friendly, professional.");
  }
  if (customInstruction && customInstruction.trim()) {
    promptLines.push(`Follow this user instruction: ${customInstruction.trim()}`);
  }

  parts.push({ text: promptLines.join("\n") });

  const result = await callGemini(parts, c.env ?? {}, tone);
  if (result.status === 200 && "replies" in result.body) {
    result.body.user = await incrementUserUsage(c.env ?? {}, userId);
  }
  return c.json(result.body, result.status as 200 | 400 | 503);
});

app.post("/v1/replies", enforceRequestGuards, async (c) => {
  let payload: Record<string, unknown>;
  try {
    payload = await c.req.json();
  } catch {
    return c.json({ detail: "Invalid JSON body." }, 422);
  }

  const conversationText =
    typeof payload.conversation_text === "string" ? payload.conversation_text : "";
  const tone = typeof payload.tone === "string" ? payload.tone : undefined;
  const customInstruction =
    typeof payload.custom_instruction === "string"
      ? payload.custom_instruction
      : undefined;

  if (!conversationText.trim() || conversationText.length > 12_000) {
    return c.json({ detail: "conversation_text must be between 1 and 12000 characters." }, 422);
  }
  if (customInstruction && customInstruction.length > 500) {
    return c.json({ detail: "custom_instruction exceeds maximum length." }, 422);
  }
  if (tone && !ALLOWED_TONES.has(tone)) {
    return c.json({ detail: `Invalid tone: ${tone}` }, 422);
  }

  const userId = resolveUserId(c, payload);
  const userProfile = await getOrInitUser(c.env ?? {}, userId);
  if (
    userProfile.tier === "free" &&
    userProfile.remaining !== null &&
    userProfile.remaining <= 0
  ) {
    return c.json(
      {
        detail: `Daily free limit reached (${userProfile.daily_used}/${userProfile.daily_limit} used today). Upgrade to Paid for unlimited replies.`,
        user: userProfile,
      },
      429
    );
  }

  const promptLines = [
    "Analyze the visible conversation context and generate exactly 3 reply suggestions.",
    "Identify the sender of each message and focus on the latest incoming message.",
    "Do not invent facts about the user and do not assume agreement unless instructed.",
    `Conversation text:\n${conversationText.trim()}`,
  ];
  if (tone) {
    promptLines.push(`Use this tone for all 3 replies: ${tone}.`);
  } else {
    promptLines.push("Use these 3 tones in order: casual, friendly, professional.");
  }
  if (customInstruction && customInstruction.trim()) {
    promptLines.push(`Follow this user instruction: ${customInstruction.trim()}`);
  }

  const result = await callGemini([{ text: promptLines.join("\n") }], c.env ?? {}, tone);
  if (result.status === 200 && "replies" in result.body) {
    result.body.user = await incrementUserUsage(c.env ?? {}, userId);
  }
  return c.json(result.body, result.status as 200 | 400 | 503);
});

app.post("/v1/rewrite", enforceRequestGuards, async (c) => {
  let payload: Record<string, unknown>;
  try {
    payload = await c.req.json();
  } catch {
    return c.json({ detail: "Invalid JSON body." }, 422);
  }

  const replyText = typeof payload.reply_text === "string" ? payload.reply_text : "";
  const action =
    typeof payload.action === "string" ? payload.action : ("rewrite" as RewriteAction);
  const tone = typeof payload.tone === "string" ? payload.tone : undefined;
  const customInstruction =
    typeof payload.custom_instruction === "string"
      ? payload.custom_instruction
      : undefined;
  const conversationText =
    typeof payload.conversation_text === "string" ? payload.conversation_text : undefined;

  if (!replyText.trim() || replyText.length > 4_000) {
    return c.json({ detail: "reply_text must be between 1 and 4000 characters." }, 422);
  }
  if (!ALLOWED_ACTIONS.has(action)) {
    return c.json({ detail: `Invalid action: ${action}` }, 422);
  }
  if (tone && !ALLOWED_TONES.has(tone)) {
    return c.json({ detail: `Invalid tone: ${tone}` }, 422);
  }
  if (customInstruction && customInstruction.length > 500) {
    return c.json({ detail: "custom_instruction exceeds maximum length." }, 422);
  }
  if (conversationText && conversationText.length > 12_000) {
    return c.json({ detail: "conversation_text exceeds maximum length." }, 422);
  }

  const userId = resolveUserId(c, payload);
  const userProfile = await getOrInitUser(c.env ?? {}, userId);
  if (
    userProfile.tier === "free" &&
    userProfile.remaining !== null &&
    userProfile.remaining <= 0
  ) {
    return c.json(
      {
        detail: `Daily free limit reached (${userProfile.daily_used}/${userProfile.daily_limit} used today). Upgrade to Paid for unlimited replies.`,
        user: userProfile,
      },
      429
    );
  }

  const actionInstructions: Record<string, string> = {
    rewrite: "Rewrite this draft reply into 3 alternative versions.",
    shorten: "Shorten this draft reply into 3 concise versions.",
    expand:
      "Expand this draft reply into 3 more detailed versions while keeping the same facts.",
    tone: `Rewrite this draft reply into 3 versions using the '${tone || "friendly"}' tone.`,
  };

  const promptLines = [
    actionInstructions[action] ?? actionInstructions.rewrite,
    `Original draft reply:\n${replyText.trim()}`,
    "Do not invent facts about the user.",
  ];
  if (conversationText && conversationText.trim()) {
    promptLines.push(`Conversation context:\n${conversationText.trim()}`);
  }
  if (tone) {
    promptLines.push(`Target tone: ${tone}.`);
  }
  if (customInstruction && customInstruction.trim()) {
    promptLines.push(`Custom instruction: ${customInstruction.trim()}`);
  }

  const result = await callGemini([{ text: promptLines.join("\n") }], c.env ?? {}, tone);
  if (result.status === 200 && "replies" in result.body) {
    result.body.user = await incrementUserUsage(c.env ?? {}, userId);
  }
  return c.json(result.body, result.status as 200 | 400 | 503);
});

export default app;
