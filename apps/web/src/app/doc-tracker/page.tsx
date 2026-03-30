"use client";

import React, { useEffect, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { apiUrl } from '@/lib/api';
import { toast } from 'sonner';

type RegistryVessel = {
  id: string;
  imoNumber?: string;
  name?: string;
  vesselType?: string;
  buildYear?: number;
  flagState?: string;
};

type DocRow = {
  imo: string;
  name: string;
  status: string;
  step: number;
};

function computeDocStatus(v: RegistryVessel): DocRow {
  const vesselType = (v.vesselType ?? 'Container').toLowerCase();
  const buildYear = v.buildYear ?? 2018;
  const age = Math.max(0, 2025 - buildYear);

  // Deterministic rule-of-thumb progression for MVP demo.
  let step = 1;
  if (vesselType === 'lng' || vesselType === 'ro-ro' || age <= 8) step = 2;
  if (vesselType === 'container' && age <= 6) step = 3;
  if ((v.flagState ?? '').toUpperCase() === 'MALTA' && age <= 10) step = 4;

  const statusByStep = ['Missing Flexibility', 'Pending Auditor', 'In Review', 'Verification Complete', 'DoC Issued (Final)'];
  return {
    imo: (v.imoNumber ?? 'N/A').replace('IMO', ''),
    name: v.name ?? 'Unnamed Vessel',
    status: statusByStep[step],
    step
  };
}

export default function DocTracker() {
  const [docStatus, setDocStatus] = useState<DocRow[]>([]);

  useEffect(() => {
    fetch(apiUrl('/api/v1/registry/vessels'))
      .then(r => r.json())
      .then((vessels: RegistryVessel[]) => {
        if (!Array.isArray(vessels) || vessels.length === 0) return;
        setDocStatus(vessels.map(computeDocStatus));
      })
      .catch(() => {
        toast.warning('Doc Tracker Using Local Fallback', {
          description: 'Registry API unavailable, showing fallback DoC progression.'
        });
        setDocStatus([
          computeDocStatus({ id: '1', imoNumber: 'IMO9434761', name: 'MV Baltic Horizon', vesselType: 'Container', buildYear: 2017, flagState: 'Cyprus' }),
          computeDocStatus({ id: '2', imoNumber: 'IMO9762214', name: 'MV Adriatic Pioneer', vesselType: 'Ro-Ro', buildYear: 2020, flagState: 'Italy' }),
          computeDocStatus({ id: '3', imoNumber: 'IMO9385420', name: 'MT North Sea Progress', vesselType: 'Tanker', buildYear: 2014, flagState: 'Malta' }),
        ]);
      });
  }, []);

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
            </div>
          </div>
        ))}
        
      </div>
    </div>
  );
}
