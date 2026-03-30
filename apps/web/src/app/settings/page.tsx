"use client";

import React, { useState } from "react";
import { User, Lock, Users, Database } from "lucide-react";
import { toast } from "sonner";
import { API_BASE_URL } from "@/lib/api";

type SettingsSection = "profile" | "team" | "security" | "api";

const TEAM_DEMO_ROWS = [
  { name: "System Admin", email: "admin@acme-ship.com", role: "Principal Administrator (Level 1)" },
  { name: "Compliance Lead", email: "compliance@acme-ship.com", role: "Compliance Manager" },
  { name: "Pooling Desk", email: "pooling@acme-ship.com", role: "Commercial Pooling Manager" },
];

export default function SystemSettings() {
  const [section, setSection] = useState<SettingsSection>("profile");
  const [firstName, setFirstName] = useState("System Admin");
  const [lastName, setLastName] = useState("User");
  const [email, setEmail] = useState("admin@acme-ship.com");

  const handleSaveProfile = (e: React.FormEvent) => {
    e.preventDefault();
    toast.success("Settings saved", {
      description: "Profile fields updated for this session (demo; no backend user API yet).",
    });
  };

  return (
    <div className="space-y-6 max-w-[1200px] mx-auto animate-in fade-in duration-500 h-full flex flex-col">
      <div className="flex flex-col gap-2 mb-8 border-b border-slate-200 pb-6">
        <h2 className="text-3xl font-bold tracking-tight text-slate-900">System Configuration</h2>
        <p className="text-slate-500">
          Manage organizational bounds, API keys, and RBAC policies for Acme Shipmanagement.
        </p>
      </div>

      <div className="flex-1 flex flex-col md:flex-row gap-8">
        <nav
          className="w-full md:w-64 space-y-2 shrink-0"
          role="tablist"
          aria-label="Settings sections"
        >
          <SettingsTab
            icon={<User className="w-5 h-5" aria-hidden />}
            label="My Profile"
            selected={section === "profile"}
            onClick={() => setSection("profile")}
            tabId="tab-profile"
            controlsId="panel-settings"
          />
          <SettingsTab
            icon={<Users className="w-5 h-5" aria-hidden />}
            label="Team Members"
            selected={section === "team"}
            onClick={() => setSection("team")}
            tabId="tab-team"
            controlsId="panel-settings"
          />
          <SettingsTab
            icon={<Lock className="w-5 h-5" aria-hidden />}
            label="Security & RBAC"
            selected={section === "security"}
            onClick={() => setSection("security")}
            tabId="tab-security"
            controlsId="panel-settings"
          />
          <SettingsTab
            icon={<Database className="w-5 h-5" aria-hidden />}
            label="API Integration"
            selected={section === "api"}
            onClick={() => setSection("api")}
            tabId="tab-api"
            controlsId="panel-settings"
          />
        </nav>

        <div
          id="panel-settings"
          role="tabpanel"
          aria-labelledby={
            section === "profile"
              ? "tab-profile"
              : section === "team"
                ? "tab-team"
                : section === "security"
                  ? "tab-security"
                  : "tab-api"
          }
          className="flex-1 bg-white rounded-xl border border-slate-200 shadow-sm p-8 min-h-[320px]"
        >
          {section === "profile" && (
            <>
              <h3 className="text-lg font-bold text-slate-900 mb-6">Profile Information</h3>
              <form className="space-y-6 max-w-xl" onSubmit={handleSaveProfile}>
                <div className="grid grid-cols-2 gap-6">
                  <div className="space-y-2">
                    <label htmlFor="firstName" className="text-sm font-semibold text-slate-700">
                      First Name
                    </label>
                    <input
                      id="firstName"
                      type="text"
                      value={firstName}
                      onChange={(e) => setFirstName(e.target.value)}
                      className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div className="space-y-2">
                    <label htmlFor="lastName" className="text-sm font-semibold text-slate-700">
                      Last Name
                    </label>
                    <input
                      id="lastName"
                      type="text"
                      value={lastName}
                      onChange={(e) => setLastName(e.target.value)}
                      className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>
                <div className="space-y-2">
                  <label htmlFor="email" className="text-sm font-semibold text-slate-700">
                    Business Email
                  </label>
                  <input
                    id="email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div className="space-y-2">
                  <label htmlFor="orgRole" className="text-sm font-semibold text-slate-700">
                    Organizational Role
                  </label>
                  <input
                    id="orgRole"
                    type="text"
                    disabled
                    readOnly
                    value="Principal Administrator (Level 1)"
                    className="w-full px-4 py-3 bg-slate-100 border border-slate-200 rounded-lg text-sm text-slate-500 cursor-not-allowed"
                  />
                </div>
                <button
                  type="submit"
                  className="mt-8 bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 px-8 rounded-lg transition-colors shadow-sm active:scale-95"
                >
                  Save Changes
                </button>
              </form>
            </>
          )}

          {section === "team" && (
            <>
              <h3 className="text-lg font-bold text-slate-900 mb-2">Team Members</h3>
              <p className="text-sm text-slate-500 mb-6">
                Demo directory for Acme Shipmanagement. Production would sync from your identity provider
                (for example Keycloak).
              </p>
              <div className="overflow-x-auto rounded-lg border border-slate-200">
                <table className="w-full text-sm text-left">
                  <thead className="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200">
                    <tr>
                      <th className="px-4 py-3">Name</th>
                      <th className="px-4 py-3">Email</th>
                      <th className="px-4 py-3">Role</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {TEAM_DEMO_ROWS.map((row) => (
                      <tr key={row.email} className="hover:bg-slate-50/80">
                        <td className="px-4 py-3 font-medium text-slate-900">{row.name}</td>
                        <td className="px-4 py-3 text-slate-600 font-mono text-xs">{row.email}</td>
                        <td className="px-4 py-3 text-slate-600">{row.role}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {section === "security" && (
            <>
              <h3 className="text-lg font-bold text-slate-900 mb-2">Security &amp; RBAC</h3>
              <p className="text-sm text-slate-500 mb-6">
                Role matrix and enforcement are defined for production (see{" "}
                <code className="text-xs bg-slate-100 px-1 rounded">FuelEUTower.md</code> and{" "}
                <code className="text-xs bg-slate-100 px-1 rounded">.cursor/rules/fueleu-domain.mdc</code>
                ). This MVP uses permissive API security so demo workflows run without OIDC setup.
              </p>
              <ul className="list-disc pl-5 space-y-2 text-sm text-slate-700 max-w-2xl">
                <li>Planned: Keycloak/OIDC login and tenant-scoped API authorization.</li>
                <li>Planned: Commercial Pooling Manager vs Compliance Manager write scopes on pool vs ledger.</li>
                <li>Current: flexibility and ledger endpoints rely on server-side policy checks; lock down for production.</li>
              </ul>
            </>
          )}

          {section === "api" && (
            <>
              <h3 className="text-lg font-bold text-slate-900 mb-2">API Integration</h3>
              <p className="text-sm text-slate-500 mb-6">
                The web app calls the Control Tower REST API. Override with{" "}
                <code className="text-xs bg-slate-100 px-1 rounded">NEXT_PUBLIC_API_BASE_URL</code> in{" "}
                <code className="text-xs bg-slate-100 px-1 rounded">apps/web/.env.local</code> if needed.
              </p>
              <div className="space-y-2 max-w-xl">
                <label htmlFor="apiBase" className="text-sm font-semibold text-slate-700">
                  Active API base URL
                </label>
                <input
                  id="apiBase"
                  type="text"
                  readOnly
                  value={API_BASE_URL}
                  className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-lg text-sm font-mono text-slate-800"
                />
              </div>
              <p className="mt-4 text-xs text-slate-500">
                OpenAPI source: <code className="bg-slate-100 px-1 rounded">packages/openapi/api-spec.yaml</code>
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function SettingsTab({
  icon,
  label,
  selected,
  onClick,
  tabId,
  controlsId,
}: {
  icon: React.ReactNode;
  label: string;
  selected: boolean;
  onClick: () => void;
  tabId: string;
  controlsId: string;
}) {
  return (
    <button
      type="button"
      id={tabId}
      role="tab"
      aria-selected={selected}
      aria-controls={controlsId}
      onClick={onClick}
      className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-colors text-left ${
        selected
          ? "bg-slate-100 text-slate-900 border border-slate-200 shadow-sm"
          : "text-slate-600 hover:bg-slate-50 hover:text-slate-900 border border-transparent"
      }`}
    >
      <span className="w-5 h-5 shrink-0 opacity-90">{icon}</span>
      {label}
    </button>
  );
}
