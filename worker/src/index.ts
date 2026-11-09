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

export interface R2BucketBinding {
  put(
    key: string,
    value: Uint8Array | string,
    options?: {
      httpMetadata?: { contentType?: string };
      customMetadata?: Record<string, string>;
    }
  ): Promise<unknown>;
}

export interface DurableObjectStubBinding {
  fetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response>;
}

export interface DurableObjectNamespaceBinding {
  idFromName(name: string): unknown;
  get(id: unknown, options?: { locationHint?: string }): DurableObjectStubBinding;
}

export interface Env {
  GEMINI_API_KEY?: string;
  GEMINI_MODEL?: string;
  MAX_REQUEST_BYTES?: string;
  RATE_LIMIT_PER_MINUTE?: string;
  FREE_DAILY_LIMIT?: string;
  DB?: D1Binding;
  STORAGE?: R2BucketBinding;
  GEMINI_PROXY?: DurableObjectNamespaceBinding;
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

export interface StructuredChatMessage {
  index: number;
  sender: "them" | "me" | "system";
  text: string;
  alignment?: "left" | "right" | "center";
}

export interface StructuredChatContext {
  source?: string;
  language_hint?: string;
  reply_language_directive?: string;
  last_speaker?: string;
  message_intent?: string;
  user_style_hint?: string;
  target_word_count_range?: string;
  reply_slot_plans?: string[];
  latest_incoming_message?: string;
  messages: StructuredChatMessage[];
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

export const SYSTEM_PROMPT = `You are WingAI, a fast chat reply generator.
The client has ALREADY analyzed the conversation language, user style, message intent, and 3 reply slot plans.
Do NOT re-analyze input language—strictly obey the provided Reply Language Directive, User Style, Target Word Count, and 3 Slot Plans.
Do not invent facts.
Return ONLY valid JSON with this exact shape: {"status":"success","context_status":"sufficient","message":null,"replies":[{"tone":"casual","text":"..."},{"tone":"friendly","text":"..."},{"tone":"professional","text":"..."}]}.`;

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

// In-memory fallback store and fast cache for user profiles
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
  const cached = memoryUsers.get(userId);
  if (cached && cached.usage_date === today && env.DB) {
    return toUserProfile(cached, env);
  }
  if (env.DB) {
    const existing = await env.DB.prepare(
      "SELECT user_id, tier, daily_used, usage_date FROM users WHERE user_id = ?"
    )
      .bind(userId)
      .first<{ user_id: string; tier: string; daily_used: number; usage_date: string }>();

    if (!existing) {
      const fresh = { user_id: userId, tier: "free" as UserTier, daily_used: 0, usage_date: today };
      memoryUsers.set(userId, fresh);
      await env.DB.prepare(
        "INSERT INTO users (user_id, tier, daily_used, usage_date) VALUES (?, 'free', 0, ?)"
      )
        .bind(userId, today)
        .run();
      return toUserProfile(fresh, env);
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
    const row = { user_id: userId, tier, daily_used: dailyUsed, usage_date: today };
    memoryUsers.set(userId, row);
    return toUserProfile(row, env);
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

function incrementUserProfileInMemory(current: UserProfile, env: Env): UserProfile {
  const nextUsed = current.daily_used + 1;
  const today = todayUtc();
  const updated = {
    user_id: current.user_id,
    tier: current.tier,
    daily_used: nextUsed,
    usage_date: today,
  };
  memoryUsers.set(current.user_id, updated);
  return toUserProfile(updated, env);
}

async function persistUserUsageIncrement(
  env: Env,
  userId: string,
  nextUsed: number,
  today: string
): Promise<void> {
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

function base64ToBytes(base64: string): Uint8Array {
  const bin = atob(base64.trim());
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    bytes[i] = bin.charCodeAt(i);
  }
  return bytes;
}

interface ArchiveLogInput {
  env: Env;
  userId: string;
  tier: UserTier;
  endpoint: string;
  tone?: string;
  conversationText?: string;
  structuredContext?: StructuredChatContext;
  customInstruction?: string;
  replyText?: string;
  imageBase64?: string;
  mimeType?: string;
  statusCode: number;
  responseBody: ReplyResponse | { detail: string };
  latencyMs: number;
}

async function archiveRequestToR2AndD1(input: ArchiveLogInput): Promise<void> {
  const requestId = crypto.randomUUID();
  const createdAt = new Date().toISOString();
  const datePrefix = createdAt.slice(0, 10);
  const safeUserId = input.userId.replace(/[^a-zA-Z0-9_-]/g, "_") || "anonymous";

  let imageR2Key: string | null = null;
  if (input.imageBase64 && input.imageBase64.trim() && input.env.STORAGE) {
    const ext = input.mimeType?.includes("png") ? "png" : "jpg";
    imageR2Key = `images/${datePrefix}/${safeUserId}/${requestId}.${ext}`;
    try {
      const bytes = base64ToBytes(input.imageBase64);
      await input.env.STORAGE.put(imageR2Key, bytes, {
        httpMetadata: { contentType: input.mimeType || "image/jpeg" },
        customMetadata: {
          request_id: requestId,
          user_id: input.userId,
          tier: input.tier,
          endpoint: input.endpoint,
          created_at: createdAt,
        },
      });
    } catch (err) {
      console.error("Failed to save image to R2:", err);
    }
  }

  const logR2Key = `logs/${datePrefix}/${safeUserId}/${requestId}.json`;
  const logEntry = {
    id: requestId,
    created_at: createdAt,
    user_id: input.userId,
    tier: input.tier,
    endpoint: input.endpoint,
    tone: input.tone ?? null,
    has_image: Boolean(imageR2Key),
    image_r2_key: imageR2Key,
    conversation_text: input.conversationText ?? null,
    structured_context: input.structuredContext ?? null,
    custom_instruction: input.customInstruction ?? null,
    reply_text: input.replyText ?? null,
    status_code: input.statusCode,
    latency_ms: input.latencyMs,
    response: input.responseBody,
  };

  console.log(
    JSON.stringify({
      event: "wingai_request",
      id: requestId,
      created_at: createdAt,
      user_id: input.userId,
      tier: input.tier,
      endpoint: input.endpoint,
      has_image: Boolean(imageR2Key),
      image_r2_key: imageR2Key,
      log_r2_key: logR2Key,
      status_code: input.statusCode,
      latency_ms: input.latencyMs,
    })
  );

  if (input.env.STORAGE) {
    try {
      await input.env.STORAGE.put(logR2Key, JSON.stringify(logEntry, null, 2), {
        httpMetadata: { contentType: "application/json" },
        customMetadata: {
          request_id: requestId,
          user_id: input.userId,
          endpoint: input.endpoint,
          status_code: String(input.statusCode),
        },
      });
    } catch (err) {
      console.error("Failed to save log JSON to R2:", err);
    }
  }

  if (input.env.DB) {
    try {
      const repliesJson =
        "replies" in input.responseBody
          ? JSON.stringify(input.responseBody.replies)
          : JSON.stringify(input.responseBody);
      await input.env.DB.prepare(
        `INSERT INTO request_logs (
          id, created_at, user_id, tier, endpoint, tone, has_image,
          image_r2_key, log_r2_key, conversation_text, status_code, replies_json, latency_ms
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      )
        .bind(
          requestId,
          createdAt,
          input.userId,
          input.tier,
          input.endpoint,
          input.tone ?? null,
          imageR2Key ? 1 : 0,
          imageR2Key,
          logR2Key,
          input.conversationText ?? input.replyText ?? null,
          input.statusCode,
          repliesJson,
          input.latencyMs
        )
        .run();
    } catch (err) {
      console.error("Failed to insert request_logs into D1:", err);
    }
  }
}

function scheduleArchive(c: Context<{ Bindings: Env }>, task: Promise<void>): void {
  try {
    if (c.executionCtx && typeof c.executionCtx.waitUntil === "function") {
      c.executionCtx.waitUntil(task);
    }
  } catch {
    // In unit tests without an ExecutionContext, task still runs in background
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

  const primaryModel = env.GEMINI_MODEL?.trim() || "gemini-3.5-flash-lite";
  const candidateModels = [primaryModel];

  const buildRequestBody = (_modelName: string) => ({
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
      temperature: 0.3,
      thinkingConfig: {
        thinkingLevel: "MINIMAL",
      },
    },
    safetySettings: [
      { category: "HARM_CATEGORY_HARASSMENT", threshold: "BLOCK_NONE" },
      { category: "HARM_CATEGORY_HATE_SPEECH", threshold: "BLOCK_NONE" },
      { category: "HARM_CATEGORY_SEXUALLY_EXPLICIT", threshold: "BLOCK_NONE" },
      { category: "HARM_CATEGORY_DANGEROUS_CONTENT", threshold: "BLOCK_NONE" },
    ],
  });

  const fetchFn =
    customFetcher ??
    (async (targetUrl: string, init?: RequestInit): Promise<Response> => {
      const directResp = await fetch(targetUrl, init);
      if (directResp.ok) {
        return directResp;
      }
      // Only fall back to US Durable Object proxy if direct edge is geo-restricted
      if ((directResp.status === 400 || directResp.status === 403) && env.GEMINI_PROXY) {
        const clonedText = await directResp.clone().text().catch(() => "");
        if (clonedText.toLowerCase().includes("location")) {
          try {
            const doId = env.GEMINI_PROXY.idFromName("gemini-us-wnam-v1");
            const stub = env.GEMINI_PROXY.get(doId, { locationHint: "wnam" });
            const proxyReq = new Request("https://gemini-proxy.internal/forward", {
              method: "POST",
              headers: {
                "Content-Type": "application/json",
                "X-Target-Url": targetUrl,
              },
              body: init?.body,
            });
            return await stub.fetch(proxyReq);
          } catch {
            return directResp;
          }
        }
      }
      return directResp;
    });
  let upstreamResponse: Response | null = null;
  let upstreamJson: any = null;
  let rawText: string | undefined;
  const errorTrace: string[] = [];

  for (let i = 0; i < candidateModels.length; i++) {
    const model = candidateModels[i];
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(
      model
    )}:generateContent?key=${encodeURIComponent(apiKey)}`;

    try {
      upstreamResponse = await fetchFn(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(buildRequestBody(model)),
      });
    } catch (err: any) {
      errorTrace.push(`net_err(${model}): ${err?.message || "fetch failed"}`);
      continue;
    }

    if (!upstreamResponse.ok) {
      let errText = "";
      try {
        errText = await upstreamResponse.text();
      } catch {
        errText = "";
      }
      errorTrace.push(
        `http_${upstreamResponse.status}(${model})${
          errText ? `: ${errText.replace(/\s+/g, " ").slice(0, 140)}` : ""
        }`
      );
      continue;
    }

    try {
      upstreamJson = await upstreamResponse.json();
    } catch {
      errorTrace.push(`invalid_json(${model})`);
      continue;
    }

    rawText = upstreamJson?.candidates?.[0]?.content?.parts?.[0]?.text;
    if (rawText && rawText.trim()) {
      break;
    }

    const blockReason =
      upstreamJson?.promptFeedback?.blockReason ||
      upstreamJson?.candidates?.[0]?.finishReason ||
      "empty_candidates";
    errorTrace.push(`blocked(${model}:${blockReason})`);
  }

  if (!rawText || !rawText.trim()) {
    return {
      status: 503,
      body: {
        detail:
          errorTrace.join(" | ") || "AI service failed to process the request.",
      },
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

function parseStructuredContext(raw: unknown): StructuredChatContext | undefined {
  if (!raw || typeof raw !== "object") return undefined;
  const obj = raw as Record<string, unknown>;
  if (!Array.isArray(obj.messages) || obj.messages.length === 0) return undefined;

  const messages: StructuredChatMessage[] = [];
  for (let i = 0; i < obj.messages.length; i++) {
    const item = obj.messages[i];
    if (!item || typeof item !== "object") continue;
    const m = item as Record<string, unknown>;
    const text = typeof m.text === "string" ? m.text.trim() : "";
    if (!text) continue;
    const rawSender = typeof m.sender === "string" ? m.sender.toLowerCase() : "them";
    const sender: "them" | "me" | "system" =
      rawSender === "me" ? "me" : rawSender === "system" ? "system" : "them";
    const rawAlign = typeof m.alignment === "string" ? m.alignment.toLowerCase() : undefined;
    const alignment: "left" | "right" | "center" | undefined =
      rawAlign === "right" || rawAlign === "center" || rawAlign === "left"
        ? rawAlign
        : undefined;
    messages.push({
      index: typeof m.index === "number" ? m.index : messages.length + 1,
      sender,
      text: text.slice(0, 1500),
      alignment,
    });
  }

  if (messages.length === 0) return undefined;

  const slotPlans: string[] = [];
  if (Array.isArray(obj.reply_slot_plans)) {
    for (const plan of obj.reply_slot_plans.slice(0, 3)) {
      if (typeof plan === "string" && plan.trim()) {
        slotPlans.push(plan.trim().slice(0, 300));
      }
    }
  }

  return {
    source: typeof obj.source === "string" ? obj.source.slice(0, 64) : "ocr_select_and_search",
    language_hint: typeof obj.language_hint === "string" ? obj.language_hint.slice(0, 32) : "auto",
    reply_language_directive:
      typeof obj.reply_language_directive === "string"
        ? obj.reply_language_directive.trim().slice(0, 400)
        : undefined,
    last_speaker:
      typeof obj.last_speaker === "string" ? obj.last_speaker.trim().slice(0, 16) : undefined,
    message_intent:
      typeof obj.message_intent === "string" ? obj.message_intent.trim().slice(0, 64) : undefined,
    user_style_hint:
      typeof obj.user_style_hint === "string" ? obj.user_style_hint.trim().slice(0, 64) : undefined,
    target_word_count_range:
      typeof obj.target_word_count_range === "string"
        ? obj.target_word_count_range.trim().slice(0, 32)
        : undefined,
    reply_slot_plans: slotPlans.length > 0 ? slotPlans : undefined,
    latest_incoming_message:
      typeof obj.latest_incoming_message === "string"
        ? obj.latest_incoming_message.trim().slice(0, 1500)
        : messages.filter((m) => m.sender === "them").slice(-1)[0]?.text ??
          messages[messages.length - 1].text,
    messages,
  };
}

function formatStructuredContextPrompt(ctx: StructuredChatContext): string {
  const turns = ctx.messages
    .map((m) => {
      const roleLabel =
        m.sender === "me"
          ? "USER (Me)"
          : m.sender === "system"
          ? "CONTEXT"
          : "PARTNER (Them)";
      return `  ${m.index}. [${roleLabel}]: ${m.text}`;
    })
    .join("\n");

  const lines = [
    "=== PRE-ANALYZED CONVERSATION SPEC ===",
    `Language Hint: ${ctx.language_hint || "auto"}`,
  ];
  if (ctx.reply_language_directive) {
    lines.push(`Reply Language Directive (MANDATORY): ${ctx.reply_language_directive}`);
  }
  if (ctx.user_style_hint) {
    lines.push(`User Writing Style: ${ctx.user_style_hint}`);
  }
  if (ctx.target_word_count_range) {
    lines.push(`Target Length Per Reply: ${ctx.target_word_count_range}`);
  }
  if (ctx.message_intent) {
    lines.push(`Detected Message Intent: ${ctx.message_intent}`);
  }
  if (ctx.last_speaker) {
    lines.push(`Last Speaker on Screen: ${ctx.last_speaker}`);
  }
  if (ctx.reply_slot_plans && ctx.reply_slot_plans.length > 0) {
    lines.push("Required 3-Slot Reply Plan:");
    for (const plan of ctx.reply_slot_plans) {
      lines.push(`  - ${plan}`);
    }
  }
  lines.push(`Turn-by-Turn Conversation (${ctx.messages.length} messages, oldest to newest):`);
  lines.push(turns);
  if (ctx.latest_incoming_message) {
    lines.push(`Latest Incoming Message Requiring Reply: "${ctx.latest_incoming_message}"`);
  }
  lines.push("======================================");
  return lines.join("\n");
}

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
  const structuredContext = parseStructuredContext(payload.structured_context);
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
  const hasStructured = Boolean(structuredContext && structuredContext.messages.length > 0);
  if (!hasImage && !hasText && !hasStructured) {
    return c.json(
      { detail: "Provide structured_context, conversation_text, or image_base64." },
      422
    );
  }

  const parts: Array<Record<string, unknown>> = [];
  if (!hasStructured && hasImage && imageBase64) {
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
    "Analyze the conversation context and generate exactly 3 reply suggestions.",
    "Identify the sender of each message and focus on replying to the latest incoming message from PARTNER (Them).",
    "Do not invent facts about the user and do not assume agreement unless instructed.",
  ];
  if (hasStructured && structuredContext) {
    promptLines.push(formatStructuredContextPrompt(structuredContext));
  } else if (hasText && conversationText) {
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

  const startedAt = Date.now();
  const result = await callGemini(parts, c.env ?? {}, tone);
  const latencyMs = Date.now() - startedAt;
  let usagePersistPromise: Promise<void> = Promise.resolve();
  if (result.status === 200 && "replies" in result.body) {
    const updatedProfile = incrementUserProfileInMemory(userProfile, c.env ?? {});
    result.body.user = updatedProfile;
    usagePersistPromise = persistUserUsageIncrement(
      c.env ?? {},
      userId,
      updatedProfile.daily_used,
      updatedProfile.usage_date
    );
  }
  const transcriptForLog =
    conversationText ??
    (structuredContext
      ? structuredContext.messages.map((m) => `[${m.sender.toUpperCase()}]: ${m.text}`).join("\n")
      : undefined);
  scheduleArchive(
    c,
    Promise.all([
      usagePersistPromise,
      archiveRequestToR2AndD1({
        env: c.env ?? {},
        userId,
        tier: userProfile.tier,
        endpoint: "/v1/analyze",
        tone,
        conversationText: transcriptForLog,
        structuredContext,
        customInstruction,
        imageBase64: hasStructured ? undefined : imageBase64,
        mimeType,
        statusCode: result.status,
        responseBody: result.body,
        latencyMs,
      }),
    ]).then(() => undefined)
  );
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

  const startedAt = Date.now();
  const result = await callGemini([{ text: promptLines.join("\n") }], c.env ?? {}, tone);
  const latencyMs = Date.now() - startedAt;
  let usagePersistPromise: Promise<void> = Promise.resolve();
  if (result.status === 200 && "replies" in result.body) {
    const updatedProfile = incrementUserProfileInMemory(userProfile, c.env ?? {});
    result.body.user = updatedProfile;
    usagePersistPromise = persistUserUsageIncrement(
      c.env ?? {},
      userId,
      updatedProfile.daily_used,
      updatedProfile.usage_date
    );
  }
  scheduleArchive(
    c,
    Promise.all([
      usagePersistPromise,
      archiveRequestToR2AndD1({
        env: c.env ?? {},
        userId,
        tier: userProfile.tier,
        endpoint: "/v1/replies",
        tone,
        conversationText,
        customInstruction,
        statusCode: result.status,
        responseBody: result.body,
        latencyMs,
      }),
    ]).then(() => undefined)
  );
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
  const languageDirective =
    typeof payload.reply_language_directive === "string"
      ? payload.reply_language_directive.trim().slice(0, 400)
      : undefined;
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
  if (languageDirective) {
    promptLines.push(`Reply Language Directive (MANDATORY): ${languageDirective}`);
  }
  if (conversationText && conversationText.trim()) {
    promptLines.push(`Conversation context:\n${conversationText.trim()}`);
  }
  if (tone) {
    promptLines.push(`Target tone: ${tone}.`);
  }
  if (customInstruction && customInstruction.trim()) {
    promptLines.push(`Custom instruction: ${customInstruction.trim()}`);
  }

  const startedAt = Date.now();
  const result = await callGemini([{ text: promptLines.join("\n") }], c.env ?? {}, tone);
  const latencyMs = Date.now() - startedAt;
  let usagePersistPromise: Promise<void> = Promise.resolve();
  if (result.status === 200 && "replies" in result.body) {
    const updatedProfile = incrementUserProfileInMemory(userProfile, c.env ?? {});
    result.body.user = updatedProfile;
    usagePersistPromise = persistUserUsageIncrement(
      c.env ?? {},
      userId,
      updatedProfile.daily_used,
      updatedProfile.usage_date
    );
  }
  scheduleArchive(
    c,
    Promise.all([
      usagePersistPromise,
      archiveRequestToR2AndD1({
        env: c.env ?? {},
        userId,
        tier: userProfile.tier,
        endpoint: "/v1/rewrite",
        tone,
        conversationText,
        customInstruction,
        replyText,
        statusCode: result.status,
        responseBody: result.body,
        latencyMs,
      }),
    ]).then(() => undefined)
  );
  return c.json(result.body, result.status as 200 | 400 | 503);
});

export class GeminiRegionalProxy {
  constructor(_state: unknown, _env: Env) {}

  async fetch(request: Request): Promise<Response> {
    const targetUrl = request.headers.get("X-Target-Url");
    if (!targetUrl) {
      return new Response(JSON.stringify({ error: "Missing X-Target-Url" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }
    const bodyText = await request.text();
    const upstream = await fetch(targetUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: bodyText,
    });
    const respText = await upstream.text();
    return new Response(respText, {
      status: upstream.status,
      headers: { "Content-Type": "application/json" },
    });
  }
}

export default app;
