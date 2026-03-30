"use client";

import React, { useState, useEffect } from 'react';
import { Archive, DownloadCloud, AlertTriangle, ArrowRight, ShieldAlert, X } from 'lucide-react';
import { toast } from 'sonner';
import { apiUrl } from '@/lib/api';

export default function FlexibilityControls() {
  const [activeModal, setActiveModal] = useState<'bank' | 'borrow' | 'pool' | null>(null);
  
  // Dynamic API State
  const [vessels, setVessels] = useState<{id: string, name: string, imoNumber: string}[]>([]);
  const [selectedVessel, setSelectedVessel] = useState('');
  const [amount, setAmount] = useState('1500');

  useEffect(() => {
    // Hydrate dynamic dropdown by calling Registry Component we built prior
    fetch(apiUrl('/api/v1/registry/vessels'))
      .then(res => res.json())
      .then(data => {
        if (data && data.length > 0) {
          setVessels(data);
          setSelectedVessel(data[0].id); // default preselect
        }
      })
      .catch(e => console.error("API Offline. Dropdown isolated."));
  }, []);

  const handleExecute = async (action: string) => {
    if (vessels.length === 0) {
      toast.error("No Vessel Available", { description: "Register at least one vessel before executing flexibility actions." });
      return;
    }

    if (vessels.length > 0 && !selectedVessel) {
       toast.error("Invalid Configuration", { description: "Please explicitly bind a Vessel target to this transaction." });
       return;
    }

    try {
      const res = await fetch(apiUrl('/api/v1/flexibility/execute'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ 
           type: action, 
           vesselId: selectedVessel, 
           amount: amount 
        })
      });

      if (res.ok) {
        const data = await res.json().catch(() => null);
        toast.success(`Flexibility Record Secured`, {
          description: data?.message ?? `The ${action.toUpperCase()} action for ${amount} MJ was synchronized to PostgreSQL correctly.`
        });
      } else {
        const errorPayload = await res.json().catch(() => null);
        const backendMessage = errorPayload?.message ?? `Request failed with HTTP ${res.status}.`;
        if (res.status === 409 || res.status === 400) {
          toast.error(`Policy Check Blocked ${action.toUpperCase()}`, {
            description: backendMessage
          });
        } else {
          toast.error(`Flexibility Execution Failed`, {
            description: backendMessage
          });
        }
        return;
      }
    } catch(err) {
      toast.warning(`Backend Unreachable`, {
        description: `Could not reach API for ${action.toUpperCase()} action.`
      });
      return;
    }

    setActiveModal(null);
  };

  return (
    <div className="space-y-6 max-w-[1400px] mx-auto animate-in fade-in duration-500">
      
      <div className="flex flex-col gap-2 mb-8">
        <h2 className="text-3xl font-bold tracking-tight text-slate-900">Flexibility Workspace</h2>
        <p className="text-slate-500">Operation center for executing Banking, Borrowing, and Sub-Fleet Pooling transactions.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Bank Surplus Card */}
        <div className="bg-white rounded-2xl border border-emerald-200 shadow-sm p-8 flex flex-col hover:shadow-md transition-shadow">
          <div className="w-12 h-12 bg-emerald-100 text-emerald-600 rounded-xl flex items-center justify-center mb-6">
            <Archive className="w-6 h-6" />
          </div>
          <h3 className="text-xl font-bold text-slate-900 mb-2">Bank Surplus Amount</h3>
          <p className="text-slate-500 text-sm mb-6 flex-1">
            Secure positive VCB balance for future reporting periods. Highly regulated timeline block post DoC issuance.
          </p>
          <button onClick={() => setActiveModal('bank')} className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-bold py-3 rounded-xl transition-colors shadow-sm">
            Execute Banking Operations
          </button>
        </div>

        {/* Borrow Deficit Card */}
        <div className="bg-white rounded-2xl border border-amber-200 shadow-sm p-8 flex flex-col hover:shadow-md transition-shadow">
          <div className="w-12 h-12 bg-amber-100 text-amber-600 rounded-xl flex items-center justify-center mb-6">
            <DownloadCloud className="w-6 h-6" />
          </div>
          <h3 className="text-xl font-bold text-slate-900 mb-2">Borrow Against Deficit</h3>
          <p className="text-slate-500 text-sm mb-6 flex-1">
            Draw down against future periods to clear current residual negative balances. Includes 1.1x penalty.
          </p>
          
          <div className="p-3 bg-amber-50 rounded-lg border border-amber-200 flex gap-2 mb-6 shadow-sm">
            <AlertTriangle className="w-4 h-4 text-amber-600 flex-shrink-0 mt-0.5" />
            <p className="text-xs text-amber-900 leading-tight">
              <b>Warning:</b> Ensure vessel did not log a borrow operation in the immediate prior year.
            </p>
          </div>

          <button onClick={() => setActiveModal('borrow')} className="w-full bg-slate-900 hover:bg-slate-800 text-white font-bold py-3 rounded-xl transition-colors shadow-sm">
            Formulate Borrow Action
          </button>
        </div>

        {/* Pooling Card */}
        <div className="bg-white rounded-2xl border border-blue-200 shadow-sm p-8 flex flex-col hover:shadow-md transition-shadow">
          <div className="w-12 h-12 bg-blue-100 text-blue-600 rounded-xl flex items-center justify-center mb-6">
            <ArrowRight className="w-6 h-6" />
          </div>
          <h3 className="text-xl font-bold text-slate-900 mb-2">Establish Sub-Pool</h3>
          <p className="text-slate-500 text-sm mb-6 flex-1">
            Mathematically offset underperforming vessels with gross overperformers across defined subsets.
          </p>

          <div className="p-3 bg-red-50 rounded-lg border border-red-200 flex gap-2 mb-6 shadow-sm">
            <ShieldAlert className="w-4 h-4 text-red-600 flex-shrink-0 mt-0.5" />
            <p className="text-xs text-red-900 leading-tight">
              <b>Regulatory Limit:</b> Pool net aggregation must equal a positive integer to achieve verified validity.
            </p>
          </div>

          <button onClick={() => setActiveModal('pool')} className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 rounded-xl transition-colors shadow-sm">
            Manage Pool Strategies
          </button>
        </div>

      </div>

      {/* Shared Action Modal Overlay */}
      {activeModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg overflow-hidden animate-in zoom-in-95 duration-200">
            <div className="flex justify-between items-center p-6 border-b border-slate-100 bg-slate-50">
              <h3 className="text-xl font-bold text-slate-900 capitalize">Execute {activeModal} Strategy</h3>
              <button onClick={() => setActiveModal(null)} className="text-slate-400 hover:text-slate-600 transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <div className="p-6">
              {activeModal === 'bank' && <p className="text-sm text-slate-600 mb-6">Input the verified surplus (MJ) you wish to securely bank for the next verification period. Target vessel must be specified.</p>}
              {activeModal === 'borrow' && <p className="text-sm text-slate-600 mb-6">Select the specific vessel deficit to cover. Be aware that the required borrowing amount will mathematically increase by <b>10%</b> due to the statutory multiplier penalty.</p>}
              {activeModal === 'pool' && <p className="text-sm text-slate-600 mb-6">Initialize a new Compliance Pool allocation targeting the designated constituent.</p>}
              
              <div className="space-y-5">
                {vessels.length > 0 ? (
                  <div>
                    <label className="text-sm font-semibold text-slate-700 block mb-2">Target Vessel Selection</label>
                    <select 
                      value={selectedVessel} 
                      onChange={(e) => setSelectedVessel(e.target.value)}
                      className="w-full px-4 py-3 bg-slate-100 border border-slate-300 rounded-lg text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 font-medium"
                    >
                       {vessels.map(v => <option key={v.id} value={v.id}>{v.name} (IMO: {v.imoNumber || v.id})</option>)}
                    </select>
                  </div>
                ) : (
                  <div className="p-4 bg-slate-100 rounded-lg text-sm text-slate-500">Live Vessel Registry Offline - Simulation Mode.</div>
                )}
                
                <div>
                  <label className="text-sm font-semibold text-slate-700 block mb-2">Simulation Mechanism Total (MJ)</label>
                  <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} className="w-full px-4 py-3 text-lg font-mono bg-white border border-slate-300 rounded-lg text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
              </div>
            </div>

            <div className="p-6 border-t border-slate-100 flex gap-4">
              <button onClick={() => setActiveModal(null)} className="flex-1 py-3 font-semibold text-slate-600 bg-slate-100 rounded-xl hover:bg-slate-200 transition-colors">Cancel</button>
              <button onClick={() => handleExecute(activeModal)} className="flex-1 py-3 font-bold text-white bg-slate-900 hover:bg-slate-800 rounded-xl shadow-lg transition-transform active:scale-95">Commit Transaction</button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
