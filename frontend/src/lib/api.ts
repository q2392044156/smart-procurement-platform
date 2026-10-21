import { Conversation, ChatRequest, ResumeRequest } from "./types";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api";

async function responseError(response: Response, fallback: string): Promise<Error> {
  try {
    const body = await response.json();
    return new Error(body.detail || body.message || fallback);
  } catch {
    return new Error(fallback);
  }
}

// ===== 对话 API =====
export async function streamChat(
  request: ChatRequest,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const response = await fetch(`${BASE_URL}/chat/stream`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok) {
    throw await responseError(response, `Stream failed: ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) throw new Error("No response body");

  const decoder = new TextDecoder();
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    onChunk(decoder.decode(value, { stream: true }));
  }
}

export async function resumeChat(
  request: ResumeRequest,
  onChunk: (chunk: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const response = await fetch(
    `${BASE_URL}/chat/${request.thread_id}/resume`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
      signal,
    }
  );

  if (!response.ok) {
    throw await responseError(response, `Resume failed: ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) throw new Error("No response body");

  const decoder = new TextDecoder();
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    onChunk(decoder.decode(value, { stream: true }));
  }
}

export async function getChatState(threadId: string) {
  const res = await fetch(`${BASE_URL}/chat/${threadId}/state`);
  if (!res.ok) throw new Error(`State failed: ${res.status}`);
  return res.json();
}

// ===== 历史 API =====
const USER_ID = "user-001";

export async function getConversations(): Promise<Conversation[]> {
  const res = await fetch(`${BASE_URL}/history?user_id=${USER_ID}`);
  if (!res.ok) throw new Error(`History failed: ${res.status}`);
  const data = await res.json();
  return data.conversations ?? [];
}

export async function getMessages(threadId: string) {
  const res = await fetch(`${BASE_URL}/history/${threadId}/messages`);
  if (!res.ok) throw new Error(`Messages failed: ${res.status}`);
  const data = await res.json();
  return data.messages ?? [];
}

export async function deleteConversation(threadId: string): Promise<void> {
  const res = await fetch(`${BASE_URL}/history/${threadId}`, {
    method: "DELETE",
  });
  if (!res.ok) throw new Error(`Delete failed: ${res.status}`);
}
