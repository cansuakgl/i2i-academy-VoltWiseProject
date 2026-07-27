import { MailCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { api, ApiError } from "../api";

type NotificationPreferences = {
  emailEnabled: boolean;
  usageMilestoneEnabled: boolean;
  anomalyAlertEnabled: boolean;
  monthlySummaryEnabled: boolean;
};

export function NotificationPreferencesPanel({ token, onMessage }: { token: string; onMessage: (message: string) => void }) {
  const [preferences, setPreferences] = useState<NotificationPreferences>({
    emailEnabled: false,
    usageMilestoneEnabled: true,
    anomalyAlertEnabled: true,
    monthlySummaryEnabled: true
  });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.getNotificationPreferences(token)
      .then((response) => setPreferences(response as NotificationPreferences))
      .catch((error) => onMessage(toMessage(error)));
  }, [token, onMessage]);

  async function toggleEmail() {
    const next = {
      ...preferences,
      emailEnabled: !preferences.emailEnabled,
      usageMilestoneEnabled: true,
      anomalyAlertEnabled: true,
      monthlySummaryEnabled: true
    };

    setSaving(true);
    try {
      const saved = await api.updateNotificationPreferences(token, next);
      setPreferences(saved as NotificationPreferences);
      onMessage(next.emailEnabled ? "Email notifications enabled." : "Email notifications disabled.");
    } catch (error) {
      onMessage(toMessage(error));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="settings-row calm">
      <div className="settings-row-copy">
        <MailCheck size={22} />
        <p>Resident emails for milestone alerts, anomaly alerts, and monthly summaries are {preferences.emailEnabled ? "enabled" : "disabled"}.</p>
      </div>
      <button
        className={`toggle-button ${preferences.emailEnabled ? "on" : ""}`}
        type="button"
        aria-pressed={preferences.emailEnabled}
        aria-label="Toggle email notifications"
        disabled={saving}
        onClick={toggleEmail}
      >
        <span>{preferences.emailEnabled ? "On" : "Off"}</span>
      </button>
    </section>
  );
}

function toMessage(error: unknown) {
  if (error instanceof ApiError || error instanceof Error) return error.message;
  return "Could not save email preferences.";
}
