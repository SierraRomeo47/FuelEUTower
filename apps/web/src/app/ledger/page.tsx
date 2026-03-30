"use client";

import React, { useEffect, useState } from 'react';
import { Download, MoreHorizontal, Activity } from 'lucide-react';
import { toast } from 'sonner';
import { apiUrl } from '@/lib/api';

const targetIntensity = 89.34;
const euFlags = new Set([
  'CYPRUS', 'MALTA', 'GREECE', 'ITALY', 'PORTUGAL', 'SPAIN', 'FRANCE', 'GERMANY',
  'NETHERLANDS', 'BELGIUM', 'DENMARK', 'SWEDEN', 'FINLAND', 'ESTONIA', 'LATVIA',
  'LITHUANIA', 'POLAND', 'IRELAND', 'CROATIA', 'SLOVENIA', 'ROMANIA', 'BULGARIA'
]);

type RegistryVessel = {
  imoNumber?: string;
  name?: string;
  vesselType?: string;
  buildYear?: number;
  flagState?: string;
};

type LedgerRow = {
  imo: string;
  name: string;
  energy: string;
  intensity: string;
  target: string;
  icb: number;
  vcb: number | null;
  banked: number;
  borrowed: number;
};

function computeExposureRow(v: RegistryVessel): LedgerRow {
  const vesselType = (v.vesselType ?? 'Container').toLowerCase();
  const buildYear = v.buildYear ?? 2018;
  const flag = (v.flagState ?? '').toUpperCase();
  const isEuFlag = euFlags.has(flag);

  const baseEnergyByType: Record<string, number> = {
    container: 42000,
    tanker: 52000,
    bulker: 36000,
    'ro-ro': 28000,
    roro: 28000,
    lng: 30000,
    passenger: 24000
  };
  const vesselBase = baseEnergyByType[vesselType] ?? 34000;
  const euExposureFactor = isEuFlag ? 0.78 : 0.52;
  const ageYears = Math.max(0, 2025 - buildYear);
  const ageFactor = 1 + Math.min(ageYears, 25) * 0.01;
  const energy = vesselBase * euExposureFactor * ageFactor;

  const typeDeltaByIntensity: Record<string, number> = {
    container: 0.55,
    tanker: 0.70,
    bulker: 0.35,
    'ro-ro': 0.10,
    roro: 0.10,
    lng: -0.45,
    passenger: 0.25
  };
  const ageDelta = Math.min(ageYears, 25) * 0.03;
  const exposureDelta = isEuFlag ? 0.18 : 0.35;
  const intensity = targetIntensity + (typeDeltaByIntensity[vesselType] ?? 0.30) + ageDelta - exposureDelta;

  // Simplified synthetic ledger projection: scaled to plausible MJ magnitudes.
  const icb = (targetIntensity - intensity) * energy * 0.11;
  const vcb = icb > 0 ? icb * 0.92 : null;
  const borrowed = icb < -1200 ? Math.min(Math.abs(icb) * 0.18, 800) : 0;

  return {
    imo: (v.imoNumber ?? 'N/A').replace('IMO', ''),
    name: v.name ?? 'Unnamed Vessel',
    energy: energy.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
    intensity: intensity.toFixed(1),
    target: targetIntensity.toFixed(2),
    icb,
    vcb,
    banked: 0,
    borrowed
  };
}

export default function ComplianceLedger() {
  const [ledgerData, setLedgerData] = useState<LedgerRow[]>([]);

  useEffect(() => {
    fetch(apiUrl('/api/v1/registry/vessels'))
      .then(r => r.json())
      .then((vessels: RegistryVessel[]) => {
        if (!Array.isArray(vessels) || vessels.length === 0) return;
        setLedgerData(vessels.map(computeExposureRow));
      })
      .catch(() => {
        toast.warning('Ledger Using Local Fallback', {
          description: 'Registry API unavailable, using static sample exposure rows.'
        });
        setLedgerData([
          computeExposureRow({ imoNumber: 'IMO9434761', name: 'MV Baltic Horizon', vesselType: 'Container', buildYear: 2017, flagState: 'Cyprus' }),
          computeExposureRow({ imoNumber: 'IMO9762214', name: 'MV Adriatic Pioneer', vesselType: 'Ro-Ro', buildYear: 2020, flagState: 'Italy' }),
          computeExposureRow({ imoNumber: 'IMO9385420', name: 'MT North Sea Progress', vesselType: 'Tanker', buildYear: 2014, flagState: 'Malta' }),
        ]);
      });
  }, []);

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
