import type {
  AuthSession,
  HomeStatusResponse,
  RegistrationOptions,
  TariffPlan
} from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string | null
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  });

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const errorBody = await response.json();
      message = errorBody.message ?? errorBody.error ?? message;
    } catch {
      // Keep the fallback message when the server returns an empty body.
    }
    throw new ApiError(message, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export const api = {
  registerUser: (body: {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
  }) => request("/api/auth/register", { method: "POST", body: JSON.stringify(body) }),

  login: (body: { email: string; password: string }) =>
    request<AuthSession>("/api/auth/login", { method: "POST", body: JSON.stringify(body) }),

  me: (token: string) => request<AuthSession>("/api/auth/me", {}, token),

  getStatus: (token: string, operatorMode: boolean) =>
    request<HomeStatusResponse>(operatorMode ? "/api/operator/homes/status" : "/api/homes/status", {}, token),

  getRegistrationOptions: (token: string) => request<RegistrationOptions>("/api/config/registration-options", {}, token),

  registerHome: (token: string, body: unknown) =>
    request("/api/homes", { method: "POST", body: JSON.stringify(body) }, token),

  getHistory: <T = unknown>(token: string, homeId: string, kind: string, fromDate: string, toDate: string, operatorMode: boolean) => {
    const base = operatorMode ? `/api/operator/homes/${homeId}` : `/api/homes/${homeId}`;
    return request<T>(`${base}/history/${kind}?fromDate=${fromDate}&toDate=${toDate}`, {}, token);
  },

  getNotificationPreferences: (token: string) =>
    request("/api/users/me/notification-preferences", {}, token),

  updateNotificationPreferences: (token: string, body: unknown) =>
    request("/api/users/me/notification-preferences", { method: "PUT", body: JSON.stringify(body) }, token),

  listTariffPlans: (token: string) =>
    request<TariffPlan[]>("/api/admin/tariff-plans", {}, token),

  createTariffPlan: (token: string, body: unknown) =>
    request<TariffPlan>("/api/admin/tariff-plans", { method: "POST", body: JSON.stringify(body) }, token),

  deleteTariffPlan: (token: string, tariffPlanId: string) =>
    request<void>(`/api/admin/tariff-plans/${tariffPlanId}`, { method: "DELETE" }, token)
};
