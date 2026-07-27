import { Check, Home, Plus, Search, Trash2, Zap } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { api, ApiError } from "../api";
import type { ApplianceModelProfileOption, RegistrationOptions, TariffPlanOption } from "../types";

type Props = {
  token: string;
  isMobile?: boolean;
  options: RegistrationOptions | null;
  onMessage: (message: string) => void;
  onRegistered: () => void;
  setLoading: (loading: boolean) => void;
  variant?: "card" | "page";
};

type SelectedAppliance = {
  profileId: string;
  quantity: number;
};

export function HomeRegistrationPanel(props: Props) {
  const activeTariffs = useMemo(
    () => props.options?.tariffPlans.filter((plan) => plan.active) ?? [],
    [props.options]
  );
  const applianceProfiles = props.options?.applianceModelProfiles ?? [];
  const [selectedTariffId, setSelectedTariffId] = useState("");
  const [applianceSearch, setApplianceSearch] = useState("");
  const [selectedAppliances, setSelectedAppliances] = useState<SelectedAppliance[]>([]);
  const [form, setForm] = useState({
    name: "",
    city: "",
    region: "",
    addressLine1: "",
    monthlyUsageLimitKwh: "250"
  });

  useEffect(() => {
    if (!selectedTariffId && activeTariffs[0]) {
      setSelectedTariffId(activeTariffs[0].tariffPlanId);
    }
  }, [activeTariffs, selectedTariffId]);

  const selectedTariff = activeTariffs.find((plan) => plan.tariffPlanId === selectedTariffId);
  const filteredProfiles = applianceProfiles.filter((profile) =>
    [
      profile.displayName,
      profile.manufacturer,
      profile.modelName,
      profile.typeDisplayName
    ]
      .filter(Boolean)
      .join(" ")
      .toLowerCase()
      .includes(applianceSearch.trim().toLowerCase())
  );
  const selectedProfileDetails = selectedAppliances
    .map((item) => ({
      ...item,
      profile: applianceProfiles.find((profile) => profile.applianceModelProfileId === item.profileId)
    }))
    .filter((item): item is SelectedAppliance & { profile: ApplianceModelProfileOption } => Boolean(item.profile));

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!selectedTariff) {
      props.onMessage("Create at least one active tariff plan before registering a home.");
      return;
    }

    props.setLoading(true);
    try {
      await api.registerHome(props.token, {
        name: form.name,
        addressLine1: form.addressLine1 || null,
        city: form.city || null,
        region: form.region || null,
        countryCode: "TR",
        timezoneName: "Europe/Istanbul",
        billing: {
          tariffPlanId: selectedTariff.tariffPlanId,
          monthlyUsageLimitKwh: Number(form.monthlyUsageLimitKwh)
        },
        appliances: buildAppliancePayload(selectedProfileDetails)
      });
      setForm({
        name: "",
        city: "",
        region: "",
        addressLine1: "",
        monthlyUsageLimitKwh: "250"
      });
      setSelectedAppliances([]);
      props.onMessage("Home registered. You can add more appliances later.");
      props.onRegistered();
    } catch (error) {
      props.onMessage(toMessage(error));
    } finally {
      props.setLoading(false);
    }
  }

  function toggleProfile(profile: ApplianceModelProfileOption) {
    setSelectedAppliances((current) => {
      const existing = current.find((item) => item.profileId === profile.applianceModelProfileId);
      if (existing) {
        return current.filter((item) => item.profileId !== profile.applianceModelProfileId);
      }
      return [...current, { profileId: profile.applianceModelProfileId, quantity: 1 }];
    });
  }

  function updateQuantity(profileId: string, quantity: number) {
    if (quantity < 1) return;
    setSelectedAppliances((current) =>
      current.map((item) => item.profileId === profileId ? { ...item, quantity } : item)
    );
  }

  function removeProfile(profileId: string) {
    setSelectedAppliances((current) => current.filter((item) => item.profileId !== profileId));
  }

  const className = props.variant === "page" ? "registration-page registration-workspace" : "utility-panel";

  return (
    <form className={className} onSubmit={submit}>
      <section className="registration-main">
        {props.isMobile && (
          <MobileRegistrationSummary
            applianceCount={selectedAppliances.reduce((total, item) => total + item.quantity, 0)}
            homeName={form.name}
            monthlyLimit={form.monthlyUsageLimitKwh}
            selectedTariff={selectedTariff}
          />
        )}

        <div className="registration-section compact-section">
          <div className="section-title-row">
            <Home size={18} />
            <h3>Home</h3>
          </div>
          <div className="registration-field-grid">
            <label>
              <span>Home name</span>
              <input required placeholder="Kadikoy apartment" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} />
            </label>
            <label>
              <span>Monthly limit</span>
              <input required type="number" min="1" placeholder="250" value={form.monthlyUsageLimitKwh} onChange={(event) => setForm({ ...form, monthlyUsageLimitKwh: event.target.value })} />
            </label>
            <label>
              <span>City</span>
              <input placeholder="Istanbul" value={form.city} onChange={(event) => setForm({ ...form, city: event.target.value })} />
            </label>
            <label>
              <span>Region</span>
              <input placeholder="Marmara" value={form.region} onChange={(event) => setForm({ ...form, region: event.target.value })} />
            </label>
            <label className="wide-field">
              <span>Address</span>
              <input placeholder="Optional street or building note" value={form.addressLine1} onChange={(event) => setForm({ ...form, addressLine1: event.target.value })} />
            </label>
          </div>
        </div>

        <div className="registration-section tariff-section">
          <div className="section-title-row">
            <Zap size={18} />
            <h3>Tariff plan</h3>
          </div>
          <div className="tariff-list" role="listbox" aria-label="Available tariff plans">
            {activeTariffs.map((plan) => (
              <TariffCard
                key={plan.tariffPlanId}
                plan={plan}
                selected={plan.tariffPlanId === selectedTariffId}
                onSelect={() => setSelectedTariffId(plan.tariffPlanId)}
              />
            ))}
            {activeTariffs.length === 0 && (
              <p className="inline-empty">No active tariff plan is available.</p>
            )}
          </div>
        </div>

        <div className="registration-section appliance-catalog-section">
          <div className="section-title-row appliance-title-row">
            <Plus size={18} />
            <h3>Appliances</h3>
            <span>Optional</span>
          </div>
          <label className="search-field">
            <Search size={16} />
            <input placeholder="Search brand, model, or appliance type" value={applianceSearch} onChange={(event) => setApplianceSearch(event.target.value)} />
          </label>
          <div className="appliance-profile-list" role="listbox" aria-label="Appliance model profiles">
            {filteredProfiles.map((profile) => (
              <button
                key={profile.applianceModelProfileId}
                className={`appliance-profile-item ${selectedAppliances.some((item) => item.profileId === profile.applianceModelProfileId) ? "selected" : ""}`}
                type="button"
                onClick={() => toggleProfile(profile)}
              >
                <span className="selection-box" aria-hidden="true">
                  {selectedAppliances.some((item) => item.profileId === profile.applianceModelProfileId) && <Check size={14} />}
                </span>
                <span>
                  <strong>{profile.displayName ?? `${profile.manufacturer} ${profile.modelName}`}</strong>
                  <small>{profile.typeDisplayName} · {profile.manufacturer} · {formatWatts(profile.safeWattLimit)} safe limit</small>
                </span>
              </button>
            ))}
            {filteredProfiles.length === 0 && (
              <p className="inline-empty">No matching appliance profiles.</p>
            )}
          </div>
        </div>
      </section>

      {!props.isMobile && (
      <aside className="registration-summary">
        <div>
          <span className="summary-label">Setup summary</span>
          <h2>{form.name || "New monitored home"}</h2>
          <p>{selectedTariff ? selectedTariff.name : "Choose a tariff"} · {form.monthlyUsageLimitKwh || 0} kWh limit</p>
        </div>

        <div className="selected-appliance-list">
          {selectedProfileDetails.map(({ profile, quantity }) => (
            <div className="selected-appliance-item" key={profile.applianceModelProfileId}>
              <div>
                <strong>{profile.displayName ?? profile.modelName}</strong>
                <small>{profile.typeDisplayName}</small>
              </div>
              <input
                aria-label={`Quantity for ${profile.modelName}`}
                type="number"
                min="1"
                value={quantity}
                onChange={(event) => updateQuantity(profile.applianceModelProfileId, Number(event.target.value))}
              />
              <button type="button" onClick={() => removeProfile(profile.applianceModelProfileId)} aria-label={`Remove ${profile.modelName}`}>
                <Trash2 size={15} />
              </button>
            </div>
          ))}
          {selectedProfileDetails.length === 0 && (
            <p className="inline-empty left">No appliances selected yet. You can add them later.</p>
          )}
        </div>

        <button className="primary-button register-submit-button" type="submit">
          <Plus size={16} />
          Register home
        </button>
      </aside>
      )}

      {props.isMobile && (
        <section className="mobile-registration-actions">
          <p>
            {selectedProfileDetails.length === 0
              ? "No appliances selected. You can add them later."
              : `${selectedAppliances.reduce((total, item) => total + item.quantity, 0)} appliance${selectedAppliances.reduce((total, item) => total + item.quantity, 0) === 1 ? "" : "s"} selected.`}
          </p>
          <button className="primary-button register-submit-button" type="submit">
            <Plus size={16} />
            Register home
          </button>
        </section>
      )}
    </form>
  );
}

function MobileRegistrationSummary(props: {
  applianceCount: number;
  homeName: string;
  monthlyLimit: string;
  selectedTariff: TariffPlanOption | undefined;
}) {
  return (
    <section className="mobile-registration-summary">
      <span className="summary-label">Setup summary</span>
      <strong>{props.homeName || "New monitored home"}</strong>
      <small>{props.selectedTariff ? props.selectedTariff.name : "Choose a tariff"} - {props.monthlyLimit || 0} kWh limit - {props.applianceCount} appliances</small>
    </section>
  );
}

function TariffCard(props: {
  plan: TariffPlanOption;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button className={`tariff-card ${props.selected ? "selected" : ""}`} type="button" onClick={props.onSelect}>
      <span className="selection-box" aria-hidden="true">
        {props.selected && <Check size={14} />}
      </span>
      <span>
        <strong>{props.plan.name}</strong>
        <small>{props.plan.description || props.plan.code}</small>
      </span>
      <span className="tariff-rate">
        {props.plan.baseRatePerKwh} {props.plan.currencyCode}/kWh
      </span>
    </button>
  );
}

function buildAppliancePayload(selectedProfiles: Array<SelectedAppliance & { profile: ApplianceModelProfileOption }>) {
  return selectedProfiles.flatMap(({ profile, quantity }, profileIndex) =>
    Array.from({ length: quantity }, (_, quantityIndex) => {
      const sequence = quantityIndex + 1;
      const generatedCode = `${profile.typeCode}-${profile.manufacturer}-${profile.modelName}-${profileIndex + 1}-${sequence}`
        .replace(/[^a-z0-9]+/gi, "-")
        .replace(/^-|-$/g, "")
        .toLowerCase();

      return {
        applianceCode: generatedCode,
        name: quantity > 1
          ? `${profile.displayName ?? profile.typeDisplayName} ${sequence}`
          : profile.displayName ?? profile.typeDisplayName,
        typeCode: profile.typeCode,
        manufacturer: profile.manufacturer,
        modelName: profile.modelName,
        nominalWattage: profile.nominalWattage,
        safeWattLimit: profile.safeWattLimit,
        displayOrder: profileIndex + quantityIndex + 1
      };
    })
  );
}

function formatWatts(value: number | null) {
  return value == null ? "default" : `${Math.round(value)} W`;
}

function toMessage(error: unknown) {
  if (error instanceof ApiError || error instanceof Error) return error.message;
  return "Could not register the home.";
}
