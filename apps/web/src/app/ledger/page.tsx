"use client";

import React, { useEffect, useState } from 'react';
import { Download, MoreHorizontal, Activity, X } from 'lucide-react';
import { toast } from 'sonner';
import { apiUrl } from '@/lib/api';
import { asArray, fetchJson } from '@/lib/http';
import { DNV_REQUIRED_GHG_INTENSITY_2025 } from '@/lib/units';

type LedgerRow = {
  vesselId: string;
  imo: string;
  name: string;
  vesselType: string;
  energy: number;
  intensity: number;
  target: number;
  icb: number;
  vcb: number | null;
  banked: number;
  borrowed: number;
}

export default function ComplianceLedger() {
  const [ledgerData, setLedgerData] = useState<LedgerRow[]>([]);
  const [editingRow, setEditingRow] = useState<LedgerRow | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    loadRows();
  }, []);

  const loadRows = async () => {
    try {
      const payload = await fetchJson('/api/v1/compliance-ledger/rows?year=2025');
      const rows = asArray(payload);
      if (rows.length === 0) {
        setLedgerData([]);
        return;
      }
        setLedgerData(rows.map((row) => ({
          vesselId: row.vesselId,
          imo: (row.imo ?? 'N/A').toString().replace('IMO', ''),
          name: row.name ?? 'Unnamed Vessel',
          vesselType: row.vesselType ?? 'Unknown',
          energy: Number(row.energyInScope ?? 0),
          intensity: Number(row.actualIntensity ?? 0),
          target: Number(row.targetIntensity ?? DNV_REQUIRED_GHG_INTENSITY_2025),
          icb: Number(row.icb ?? 0),
          vcb: row.vcb == null ? null : Number(row.vcb),
          banked: Number(row.bankedAmount ?? 0),
          borrowed: Number(row.borrowedAmount ?? 0),
        })));
    } catch {
      toast.error('Ledger Sync Failed', {
        description: 'Backend ledger API unavailable.'
      });
    }
  };

  const handleExport = () => {
    if (!ledgerData.length) {
      toast.error('No Ledger Rows', { description: 'Nothing to export yet.' });
      return;
    }
    const header = 'IMO,Vessel,ShipType,EnergyInScope_MJ,ActualIntensity_gCO2eqPerMJ,TargetIntensity_gCO2eqPerMJ,ICB_gCO2eq,VCB_gCO2eq,Banked_gCO2eq,Borrowed_gCO2eq';
    const body = ledgerData.map((r) =>
      [r.imo, r.name, r.vesselType, r.energy, r.intensity, r.target, r.icb, r.vcb ?? '', r.banked, r.borrowed]
        .map((v) => `"${String(v).replace(/"/g, '""')}"`)
        .join(',')
    ).join('\n');
    const csv = `${header}\n${body}\n`;
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'Ledger_Export_2025.csv';
    a.click();
    URL.revokeObjectURL(url);
    toast.success('Download Complete', { description: 'Ledger CSV exported successfully.' });
  };

  const saveEdit = async () => {
    if (!editingRow) return;
    setSaving(true);
    try {
      const res = await fetch(apiUrl(`/api/v1/compliance-ledger/rows/${editingRow.vesselId}?year=2025`), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          energyInScope: editingRow.energy,
          actualIntensity: editingRow.intensity,
          targetIntensity: editingRow.target,
          icb: editingRow.icb,
          vcb: editingRow.vcb,
          bankedAmount: editingRow.banked,
          borrowedAmount: editingRow.borrowed
        })
      });
      if (!res.ok) {
        const payload = await res.json().catch(() => null);
        toast.error('Ledger Save Failed', { description: payload?.message ?? `HTTP ${res.status}` });
        return;
      }
      toast.success('Ledger Updated', { description: `${editingRow.name} values saved.` });
      setEditingRow(null);
      loadRows();
    } catch {
      toast.error('Ledger Save Failed', { description: 'Backend unreachable.' });
    } finally {
      setSaving(false);
    }
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
                <th className="px-6 py-4 text-right">Actual Intensity (gCO2eq/MJ)</th>
                <th className="px-6 py-4 text-right">Target Base (gCO2eq/MJ)</th>
                <th className="px-6 py-4 text-right bg-blue-50">ICB (gCO2eq)</th>
                <th className="px-6 py-4 text-right bg-emerald-50">VCB (gCO2eq)</th>
                <th className="px-6 py-4"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {ledgerData.map((row) => (
                <tr key={row.vesselId} className="hover:bg-slate-50 transition-colors cursor-pointer">
                  <td className="px-6 py-4">
                    <div className="font-bold text-slate-900">{row.name}</div>
                    <div className="text-slate-500 font-mono text-xs">{row.imo}</div>
                  </td>
                  <td className="px-6 py-4 text-right font-mono text-slate-600">{row.energy.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                  <td className={`px-6 py-4 text-right font-mono font-bold ${row.intensity > row.target ? 'text-red-600' : 'text-emerald-600'}`}>{row.intensity.toFixed(2)}</td>
                  <td className="px-6 py-4 text-right font-mono text-slate-500">{row.target.toFixed(2)}</td>
                  <td className={`px-6 py-4 text-right font-mono font-bold bg-blue-50/30 ${row.icb < 0 ? 'text-red-600' : 'text-emerald-600'}`}>
                    {row.icb > 0 ? '+' : ''}{row.icb.toLocaleString('en-US', {minimumFractionDigits: 2})}
                  </td>
                  <td className="px-6 py-4 text-right font-mono font-bold bg-emerald-50/30 text-emerald-700">
                    {row.vcb ? `+${row.vcb.toLocaleString('en-US', {minimumFractionDigits: 2})}` : '---'}
                  </td>
                  <td className="px-6 py-4 text-slate-300 text-right">
                    <button onClick={() => setEditingRow({ ...row })}><MoreHorizontal className="w-5 h-5 ml-auto" /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      {editingRow && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl overflow-hidden">
            <div className="flex justify-between items-center p-6 border-b border-slate-100 bg-slate-50">
              <h3 className="text-xl font-bold text-slate-900">Edit Ledger Values - {editingRow.name}</h3>
              <button onClick={() => setEditingRow(null)} className="text-slate-400 hover:text-slate-600"><X className="w-5 h-5" /></button>
            </div>
            <div className="p-6 grid grid-cols-1 md:grid-cols-2 gap-4">
              {[
                ['Energy Scope (MJ)', 'energy'],
                ['Actual Intensity (gCO2eq/MJ)', 'intensity'],
                ['Target Intensity (gCO2eq/MJ)', 'target'],
                ['ICB (gCO2eq)', 'icb'],
                ['VCB (gCO2eq)', 'vcb'],
                ['Banked (gCO2eq)', 'banked'],
                ['Borrowed (gCO2eq)', 'borrowed']
              ].map(([label, key]) => (
                <div key={key}>
                  <label className="text-sm font-semibold text-slate-700 block mb-1">{label}</label>
                  <input
                    type="number"
                    value={editingRow[key as keyof LedgerRow] ?? ''}
                    onChange={(e) => setEditingRow((prev) => prev ? ({ ...prev, [key]: e.target.value === '' ? null : Number(e.target.value) }) : prev)}
                    className="w-full px-3 py-2 border rounded-lg"
                  />
                </div>
              ))}
            </div>
            <div className="p-6 border-t border-slate-100 flex gap-3">
              <button onClick={() => setEditingRow(null)} className="flex-1 py-3 font-semibold text-slate-600 bg-slate-100 rounded-xl">Cancel</button>
              <button onClick={saveEdit} disabled={saving} className="flex-1 py-3 font-bold text-white bg-slate-900 rounded-xl disabled:opacity-60">
                {saving ? 'Saving...' : 'Save Ledger Values'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
