"use client";

import React from 'react';
import { Download, MoreHorizontal, Activity } from 'lucide-react';
import { toast } from 'sonner';

const ledgerData = [
  { imo: '9434761', name: 'MV Baltic Horizon', energy: '41,820.00', intensity: '90.2', target: '89.34', icb: -3180.40, vcb: null, banked: 0, borrowed: 800.00 },
  { imo: '9762214', name: 'MV Adriatic Pioneer', energy: '18,460.00', intensity: '88.4', target: '89.34', icb: +980.20, vcb: +980.20, banked: 0, borrowed: 0 },
  { imo: '9385420', name: 'MT North Sea Progress', energy: '52,300.00', intensity: '89.9', target: '89.34', icb: -1240.00, vcb: null, banked: 0, borrowed: 0 },
];

export default function ComplianceLedger() {
  const handleExport = () => {
    toast('Generating CSV Ledger Export...', { description: 'Re-compiling total calculations.' });
    setTimeout(() => {
      toast.success('Download Complete', { description: 'The file `Ledger_Export_2025.csv` was generated successfully.' });
    }, 1500);
  };

  return (
    <div className="space-y-6 max-w-[1600px] mx-auto animate-in fade-in duration-500">
      
      <div className="flex flex-col gap-2 mb-8">
        <h2 className="text-3xl font-bold tracking-tight text-slate-900">Compliance Ledger</h2>
        <p className="text-slate-500">Raw aggregated reporting period metrics tracking exact regulatory math per vessel.</p>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="p-4 border-b border-slate-200 flex justify-between items-center bg-slate-50">
          <div className="flex items-center gap-3">
            <Activity className="w-5 h-5 text-blue-600" />
            <span className="font-semibold text-slate-900">Live 2025 Ledger Math</span>
          </div>
          <button 
            onClick={handleExport}
            className="flex items-center gap-2 bg-white border border-slate-300 text-slate-700 px-4 py-2 rounded-lg font-medium hover:bg-slate-50 shadow-sm transition-colors text-sm active:scale-95"
          >
            <Download className="w-4 h-4" /> Export Ledger (.csv)
          </button>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="bg-slate-50 text-slate-500 font-semibold border-b border-slate-200 text-xs uppercase">
              <tr>
                <th className="px-6 py-4">IMO / Vessel</th>
                <th className="px-6 py-4 text-right">Energy Scope (MJ)</th>
                <th className="px-6 py-4 text-right">Actual Intensity</th>
                <th className="px-6 py-4 text-right">Target Base</th>
                <th className="px-6 py-4 text-right bg-blue-50">ICB (MJ)</th>
                <th className="px-6 py-4 text-right bg-emerald-50">VCB (MJ)</th>
                <th className="px-6 py-4"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {ledgerData.map((row) => (
                <tr key={row.imo} className="hover:bg-slate-50 transition-colors cursor-pointer" onClick={() => toast("Details hidden")}>
                  <td className="px-6 py-4">
                    <div className="font-bold text-slate-900">{row.name}</div>
                    <div className="text-slate-500 font-mono text-xs">{row.imo}</div>
                  </td>
                  <td className="px-6 py-4 text-right font-mono text-slate-600">{row.energy}</td>
                  <td className={`px-6 py-4 text-right font-mono font-bold ${parseFloat(row.intensity) > parseFloat(row.target) ? 'text-red-600' : 'text-emerald-600'}`}>{row.intensity}</td>
                  <td className="px-6 py-4 text-right font-mono text-slate-500">{row.target}</td>
                  <td className={`px-6 py-4 text-right font-mono font-bold bg-blue-50/30 ${row.icb < 0 ? 'text-red-600' : 'text-emerald-600'}`}>
                    {row.icb > 0 ? '+' : ''}{row.icb.toLocaleString('en-US', {minimumFractionDigits: 2})}
                  </td>
                  <td className="px-6 py-4 text-right font-mono font-bold bg-emerald-50/30 text-emerald-700">
                    {row.vcb ? `+${row.vcb.toLocaleString('en-US', {minimumFractionDigits: 2})}` : '---'}
                  </td>
                  <td className="px-6 py-4 text-slate-300 text-right">
                    <button><MoreHorizontal className="w-5 h-5 ml-auto" /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
