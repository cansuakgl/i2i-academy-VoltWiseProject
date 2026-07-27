import { useEffect, useState } from "react";
import { api, ApiError } from "./api";
import { AuthScreen } from "./components/AuthScreen";
import { DashboardPage } from "./components/DashboardPage";
import type { AuthSession, HomeStatusItem, RegistrationOptions } from "./types";

const storedTokenKey = "wattsmart.accessToken";

export function App() {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [token, setToken] = useState(() => localStorage.getItem(storedTokenKey));
  const [homes, setHomes] = useState<HomeStatusItem[]>([]);
  const [selectedHome, setSelectedHome] = useState<HomeStatusItem | null>(null);
  const [options, setOptions] = useState<RegistrationOptions | null>(null);
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewMode] = useState<"list" | "grid">("grid");

  const operatorMode = Boolean(session?.roles.some((role) => role === "ADMIN" || role === "OPERATOR"));

  useEffect(() => {
    if (!token) return;
    api.me(token)
      .then(setSession)
      .catch(() => {
        localStorage.removeItem(storedTokenKey);
        setToken(null);
      });
  }, [token]);

  useEffect(() => {
    if (!token || !session) return;
    api.getRegistrationOptions(token)
      .then(setOptions)
      .catch((error) => {
        setOptions(null);
        setMessage(toMessage(error));
      });
  }, [token, session?.userId]);

  useEffect(() => {
    if (!token || !session) return;
    void refreshStatus();
    const intervalId = window.setInterval(() => void refreshStatus(), 2000);
    return () => window.clearInterval(intervalId);
  }, [token, session?.userId, operatorMode]);

  useEffect(() => {
    if (!message) return;
    const timeoutId = window.setTimeout(() => setMessage(""), 3200);
    return () => window.clearTimeout(timeoutId);
  }, [message]);

  async function refreshStatus() {
    if (!token) return;
    try {
      const response = await api.getStatus(token, operatorMode);
      setHomes(response.homes);
      setSelectedHome((current) =>
        current ? response.homes.find((home) => home.homeId === current.homeId) ?? current : current
      );
    } catch (error) {
      setMessage(toMessage(error));
    }
  }

  function handleAuthenticated(nextSession: AuthSession) {
    localStorage.setItem(storedTokenKey, nextSession.accessToken);
    setToken(nextSession.accessToken);
    setSession(nextSession);
  }

  function logout() {
    localStorage.removeItem(storedTokenKey);
    setToken(null);
    setSession(null);
    setHomes([]);
    setSelectedHome(null);
  }

  if (!session || !token) {
    return <AuthScreen onAuthenticated={handleAuthenticated} />;
  }

  return (
    <DashboardPage
      homes={homes}
      loading={loading}
      message={message}
      operatorMode={operatorMode}
      options={options}
      selectedHome={selectedHome}
      session={session}
      token={token}
      viewMode={viewMode}
      onLogout={logout}
      onMessage={setMessage}
      onRefresh={() => void refreshStatus()}
      onRegistered={() => void refreshStatus()}
      onSelectHome={setSelectedHome}
      onSetLoading={setLoading}
      onSetViewMode={setViewMode}
    />
  );
}

function toMessage(error: unknown) {
  if (error instanceof ApiError || error instanceof Error) return error.message;
  return "Something went wrong. Please try again.";
}
