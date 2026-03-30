"use client";

import React from 'react';
import { ShieldCheck, CheckCircle2, Clock, Check } from 'lucide-react';

const mockDoCStatus = [
  { imo: '9123456', name: 'Albatross Explorer', status: 'Pending Auditor', step: 1 },
  { imo: '9987654', name: 'Nordic Supplier', status: 'DoC Issued (Final)', step: 4 },
  { imo: '9345678', name: 'Pacific Horizon', status: 'In Review', step: 2 },
  { imo: '9554411', name: 'Global Voyager', status: 'Missing Flexibility', step: 0 },
];

export default function DocTracker() {
  return (
    <div className="space-y-6 max-w-[1400px] mx-auto animate-in fade-in duration-500">
      
      <div className="flex flex-col gap-2 mb-8">
        <h2 className="text-3xl font-bold tracking-tight text-slate-900">DoC Compliance Pipeline</h2>
        <p className="text-slate-500">Visual tracking of Document of Compliance issuance per vessel through the verification chain.</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        
        {mockDoCStatus.map((v) => (
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
