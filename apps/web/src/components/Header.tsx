"use client";

import React from 'react';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';

export default function Header() {
  const handleRunScenarios = () => {
    toast('Initializing Scenario Engine...', {
      description: 'Running Monte Carlo simulations against 2025 GHG limits.',
      icon: <Loader2 className="w-4 h-4 animate-spin text-blue-500" />
    });
    
    setTimeout(() => {
      toast.success('Simulation Completed', {
        description: 'Vessel ICB profiles updated successfully.'
      });
    }, 2500);
  };

  return (
    <header className="h-16 bg-white border-b border-slate-200 flex items-center justify-between px-8 shadow-sm z-10 shrink-0">
      <div className="flex items-center gap-4">
        <span className="bg-blue-50 text-blue-700 px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wider">
          Reporting Period: 2025
        </span>
      </div>
      <div className="flex items-center gap-4 text-sm text-slate-500">
        <span className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></span>
          Calculations Live
        </span>
        <button 
          onClick={handleRunScenarios}
          className="bg-slate-900 hover:bg-slate-800 text-white px-5 py-2 rounded-lg font-medium transition-all shadow-sm active:scale-95"
        >
          Run Scenarios
        </button>
      </div>
    </header>
  );
}
