import { useState } from "react";
import { api, ApiError } from "../api";
import type { AuthSession } from "../types";
import { useMediaQuery } from "../utils/useMediaQuery";

const demoAdminCredentials = {
  email: "demo.admin@wattsmart.local",
  password: "password"
};

export function AuthScreen({ onAuthenticated }: { onAuthenticated: (session: AuthSession) => void }) {
  const isMobile = useMediaQuery("(max-width: 720px)");
  const [mode, setMode] = useState<"login" | "register">("login");
  const [form, setForm] = useState({
    email: demoAdminCredentials.email,
    password: demoAdminCredentials.password,
    firstName: "",
    lastName: ""
  });
  const [error, setError] = useState("");

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError("");
    try {
      if (mode === "register") {
        await api.registerUser(form);
      }
      const session = await api.login({ email: form.email, password: form.password });
      onAuthenticated(session);
    } catch (caught) {
      setError(toMessage(caught));
    }
  }

  return (
    <main className={`auth-shell ${isMobile ? "mobile-auth-shell" : ""}`}>
      {!isMobile && <WelcomeCopy />}

      <section className="auth-stack">
        <form className="auth-card" onSubmit={submit}>
          {isMobile && (
            <div className="mobile-auth-brand">
              <h1>WattSmart</h1>
              <p>Monitor homes, quotas, anomalies, and billing from one workspace.</p>
            </div>
          )}
          <h2>{mode === "login" ? "Sign in" : "Create account"}</h2>
          {mode === "register" && (
            <div className="two-col">
              <input placeholder="First name" value={form.firstName} onChange={(event) => setForm({ ...form, firstName: event.target.value })} />
              <input placeholder="Last name" value={form.lastName} onChange={(event) => setForm({ ...form, lastName: event.target.value })} />
            </div>
          )}
          <input type="email" placeholder="Email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
          <input type="password" placeholder="Password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} />
          {error && <div className="form-error">{error}</div>}
          <button className="primary-button" type="submit">{mode === "login" ? "Sign in" : "Create account"}</button>
          <button className="link-button" type="button" onClick={() => setMode(mode === "login" ? "register" : "login")}>
            {mode === "login" ? "Create a new account" : "Use an existing account"}
          </button>
        </form>

        {mode === "login" && <DemoAdminNote />}
      </section>

      {isMobile && <WelcomeCopy compact />}
    </main>
  );
}

function WelcomeCopy({ compact }: { compact?: boolean }) {
  return (
    <section className={`auth-copy ${compact ? "compact-auth-copy" : ""}`}>
      {!compact && <h1>WattSmart</h1>}
      {!compact && (
        <p>
          Review live consumption, quota milestones, appliance anomalies, and resident-ready energy guidance
          from one focused workspace.
        </p>
      )}
    </section>
  );
}

function DemoAdminNote() {
  return (
    <section className="demo-admin-note" aria-label="Demo admin note">
      <strong>Demo admin is prefilled.</strong>
      <span>Use these seeded credentials to open the full admin workspace.</span>
    </section>
  );
}

function toMessage(error: unknown) {
  if (error instanceof ApiError || error instanceof Error) return error.message;
  return "Something went wrong. Please try again.";
}
