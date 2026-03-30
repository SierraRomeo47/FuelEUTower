"use client";

import React, { useEffect, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { apiUrl } from '@/lib/api';
import { toast } from 'sonner';

type DocRow = {
  vesselId: string;
  imo: string;
  name: string;
  docStatus: string;
  year: number;
  status: string;
  step: number;
  canAdvance: boolean;
  nextStatus?: string;
};

export default function DocTracker() {
  const [docStatus, setDocStatus] = useState<DocRow[]>([]);
  const [updatingVesselId, setUpdatingVesselId] = useState<string | null>(null);

  const fetchStatuses = () => {
    fetch(apiUrl('/api/v1/doc-tracker/statuses?year=2025'))
      .then(r => r.json())
      .then((rows: any[]) => {
        if (!Array.isArray(rows)) return;
        setDocStatus(rows.map((row) => ({
          vesselId: row.vesselId,
          imo: (row.imo ?? 'N/A').toString().replace('IMO', ''),
          name: row.name ?? 'Unnamed Vessel',
          docStatus: row.docStatus ?? 'MISSING_FLEXIBILITY',
          year: row.year ?? 2025,
          status: row.statusLabel ?? 'Missing Flexibility',
          step: row.step ?? 0,
          canAdvance: !!row.canAdvance,
          nextStatus: row.nextStatus
        })));
      })
      .catch(() => {
        toast.error('Doc Tracker Sync Failed', {
          description: 'Could not load backend DoC statuses.'
        });
      });
  };

  useEffect(() => {
    fetchStatuses();
  }, []);

  const handleAdvance = async (row: DocRow) => {
    if (!row.canAdvance || !row.nextStatus) return;
    setUpdatingVesselId(row.vesselId);
    try {
      const res = await fetch(apiUrl('/api/v1/doc-tracker/statuses/update'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          vesselId: row.vesselId,
          docStatus: row.nextStatus,
          year: row.year
        })
      });
      if (!res.ok) {
        const err = await res.json().catch(() => null);
        toast.error('DoC Update Blocked', {
          description: err?.message ?? `HTTP ${res.status}`
        });
        return;
      }
      toast.success('DoC Status Updated', {
        description: `${row.name} advanced to next compliance stage.`
      });
      fetchStatuses();
    } catch {
      toast.error('DoC Update Failed', {
        description: 'Backend unreachable while updating DoC status.'
      });
    } finally {
      setUpdatingVesselId(null);
    }
  };

  return (
    <div className="space-y-6 max-w-[1400px] mx-auto animate-in fade-in duration-500">
      
      <div className="flex flex-col gap-2 mb-8">
        <h2 className="text-3xl font-bold tracking-tight text-slate-900">DoC Compliance Pipeline</h2>
        <p className="text-slate-500">Visual tracking of Document of Compliance issuance per vessel through the verification chain.</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        
        {docStatus.map((v) => (
          <div key={v.imo} className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 hover:shadow-md transition-shadow flex flex-col">
            <div className="flex justify-between items-start mb-4">
               <div>
                 <h4 className="font-bold text-slate-900">{v.name}</h4>
                 <p className="text-xs font-mono text-slate-500 mt-1">IMO {v.imo}</p>
               </div>
               <ShieldCheck className={`w-6 h-6 ${v.step === 4 ? 'text-emerald-500' : 'text-slate-300'}`} />
            </div>

            <div className="mt-6 flex-1 flex flex-col justify-end">
              <div className="flex justify-between text-xs font-medium mb-3 text-slate-600">
                <span>Progress Pipeline</span>
                <span className={v.step === 4 ? 'text-emerald-600' : 'text-blue-600'}>{v.status}</span>
              </div>
              <div className="w-full bg-slate-100 rounded-full h-2.5 flex overflow-hidden">
                <div className={`h-full ${v.step >= 1 ? 'bg-blue-500' : 'bg-transparent'} flex-1 border-r border-slate-200/40`}></div>
                <div className={`h-full ${v.step >= 2 ? 'bg-blue-500' : 'bg-transparent'} flex-1 border-r border-slate-200/40`}></div>
                <div className={`h-full ${v.step >= 3 ? 'bg-indigo-500' : 'bg-transparent'} flex-1 border-r border-slate-200/40`}></div>
                <div className={`h-full ${v.step >= 4 ? 'bg-emerald-500' : 'bg-transparent'} flex-1`}></div>
              </div>

              <div className="mt-5 grid grid-cols-4 text-center text-[10px] text-slate-400 font-medium">
                <span className={v.step >= 1 ? 'text-blue-600' : ''}>Flex Lock</span>
                <span className={v.step >= 2 ? 'text-blue-600' : ''}>Audit</span>
                <span className={v.step >= 3 ? 'text-indigo-600' : ''}>Verify</span>
                <span className={v.step >= 4 ? 'text-emerald-600' : ''}>Issued</span>
              </div>
              <button
                disabled={!v.canAdvance || updatingVesselId === v.vesselId}
                onClick={() => handleAdvance(v)}
                className={`mt-4 w-full rounded-lg px-3 py-2 text-xs font-semibold transition-colors ${
                  v.canAdvance
                    ? 'bg-slate-900 text-white hover:bg-slate-800'
                    : 'bg-slate-100 text-slate-400 cursor-not-allowed'
                }`}
              >
                {updatingVesselId === v.vesselId ? 'Updating...' : v.canAdvance ? 'Advance Stage' : 'DoC Finalized'}
              </button>
            </div>
          </div>
        ))}
        
      </div>
    </div>
  );
}
