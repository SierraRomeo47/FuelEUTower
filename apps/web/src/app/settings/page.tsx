"use client";

import React from 'react';
import { User, Lock, Users, Database } from 'lucide-react';
import { toast } from 'sonner';

export default function SystemSettings() {
  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    toast.success('Settings Preserved', { description: 'Organizational boundaries and profile data have been successfully saved.' });
  };

  return (
    <div className="space-y-6 max-w-[1200px] mx-auto animate-in fade-in duration-500 h-full flex flex-col">
      
      <div className="flex flex-col gap-2 mb-8 border-b border-slate-200 pb-6">
        <h2 className="text-3xl font-bold tracking-tight text-slate-900">System Configuration</h2>
        <p className="text-slate-500">Manage organizational bounds, API keys, and RBAC policies for Acme Shipmanagement.</p>
      </div>

      <div className="flex-1 flex flex-col md:flex-row gap-8">
        
        {/* Navigation */}
        <div className="w-full md:w-64 space-y-2 shrink-0">
          <SettingsTab icon={<User />} label="My Profile" active />
          <SettingsTab icon={<Users />} label="Team Members" />
          <SettingsTab icon={<Lock />} label="Security & RBAC" />
          <SettingsTab icon={<Database />} label="API Integration" />
        </div>

        {/* Content Panel */}
        <div className="flex-1 bg-white rounded-xl border border-slate-200 shadow-sm p-8">
          <h3 className="text-lg font-bold text-slate-900 mb-6">Profile Information</h3>
          
          <form className="space-y-6 max-w-xl" onSubmit={handleSave}>
            <div className="grid grid-cols-2 gap-6">
              <div className="space-y-2">
                <label className="text-sm font-semibold text-slate-700">First Name</label>
                <input type="text" defaultValue="System Admin" className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-semibold text-slate-700">Last Name</label>
                <input type="text" defaultValue="User" className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-semibold text-slate-700">Business Email</label>
              <input type="email" defaultValue="admin@acme-ship.com" className="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-semibold text-slate-700">Organizational Role</label>
              <input type="text" disabled defaultValue="Principal Administrator (Level 1)" className="w-full px-4 py-3 bg-slate-100 border border-slate-200 rounded-lg text-sm text-slate-500 cursor-not-allowed" />
            </div>

            <button type="submit" className="mt-8 bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 px-8 rounded-lg transition-colors shadow-sm active:scale-95">
              Save Changes
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

function SettingsTab({ icon, label, active = false }: { icon: React.ReactNode, label: string, active?: boolean }) {
  return (
    <button className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-colors ${active ? 'bg-slate-100 text-slate-900 border border-slate-200 shadow-sm' : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'}`}>
      <span className="w-5 h-5">{icon}</span>
      {label}
    </button>
  );
}
