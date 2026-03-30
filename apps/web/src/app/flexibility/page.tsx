"use client";

import React, { useState, useEffect } from 'react';
import { Archive, DownloadCloud, AlertTriangle, ArrowRight, ShieldAlert, X } from 'lucide-react';
import { toast } from 'sonner';
import { apiUrl } from '@/lib/api';
import { asArray, fetchJson } from '@/lib/http';
import { DNV_REQUIRED_GHG_INTENSITY_2025, UNITS } from '@/lib/units';

type PoolParticipant = { vesselId: string; name: string; participantId: string; transfer: number };
type PoolRow = { poolId: string; name: string; status: string; netBalance: number; participants: PoolParticipant[] };
type StrategyRow = {
  vesselId: string;
  recommendation: string;
  estimatedPenaltyCostEur: number;
  estimatedBuyCostEur: number;
  estimatedBorrowCostEur: number;
  benchmarkPriceEurPerCb: number;
};

export default function FlexibilityControls() {
  const [activeModal, setActiveModal] = useState<'bank' | 'borrow' | 'pool' | null>(null);
  
  // Dynamic API State
  const [vessels, setVessels] = useState<{id: string, name: string, imoNumber: string}[]>([]);
  const [ledgerByVesselId, setLedgerByVesselId] = useState<Record<string, { icb: number; targetIntensity: number; energyInScope: number }>>({});
  const [eligibilityByVesselId, setEligibilityByVesselId] = useState<Record<string, { canBank: boolean; canBorrow: boolean; canPool: boolean; hasPoolMembership: boolean; hasBorrowThisYear: boolean }>>({});
  const [selectedVessel, setSelectedVessel] = useState('');
  const [amount, setAmount] = useState('1500');
  const [marketQuote, setMarketQuote] = useState<{ priceEurPerCb: number; source: string; symbol: string } | null>(null);
  const [buyAmount, setBuyAmount] = useState('5'); // CB units (1 CB = 1 tCO2eq in marketplace convention)
  const [poolRows, setPoolRows] = useState<PoolRow[]>([]);
  const [poolSourceVessel, setPoolSourceVessel] = useState('');
  const [poolTargetVessel, setPoolTargetVessel] = useState('');
  const [poolAmount, setPoolAmount] = useState('500');
  const [editingPoolId, setEditingPoolId] = useState('');
  const [editPoolAmount, setEditPoolAmount] = useState('500');
  const [expandedPoolId, setExpandedPoolId] = useState('');
  const [selectedPoolId, setSelectedPoolId] = useState('');
  const [allocationVesselId, setAllocationVesselId] = useState('');
  const [allocationTransfer, setAllocationTransfer] = useState('0');
  const [strategyByVesselId, setStrategyByVesselId] = useState<Record<string, StrategyRow>>({});

  useEffect(() => {
    loadVessels();
    loadLedger();
    loadEligibility();
    loadMarketQuote();
    loadPools();
    loadStrategy();
  }, []);

  useEffect(() => {
    if (!activeModal || vessels.length === 0) return;
    const eligible = vessels.find(v => isEligibleForAction(v.id, activeModal));
    if (eligible && !isEligibleForAction(selectedVessel, activeModal)) {
      setSelectedVessel(eligible.id);
    }
  }, [activeModal, vessels, eligibilityByVesselId, selectedVessel]);

  async function loadVessels() {
    try {
      const payload = await fetchJson('/api/v1/registry/vessels');
      const data = asArray(payload);
      if (data.length > 0) {
        setVessels(data);
        setSelectedVessel((prev) => prev || data[0].id);
      }
    } catch {
      console.error('Vessels API unavailable.');
    }
  }

  async function loadLedger() {
    try {
      const payload = await fetchJson('/api/v1/compliance-ledger/rows?year=2025');
      const rows = asArray(payload);
      if (rows.length === 0) return;
      const map: Record<string, { icb: number; targetIntensity: number; energyInScope: number }> = {};
      rows.forEach((row: any) => {
        if (!row?.vesselId) return;
        map[row.vesselId] = {
          icb: Number(row.icb ?? 0),
          targetIntensity: Number(row.targetIntensity ?? DNV_REQUIRED_GHG_INTENSITY_2025),
          energyInScope: Number(row.energyInScope ?? 0)
        };
      });
      setLedgerByVesselId(map);
    } catch {
      console.error('Ledger API unavailable.');
    }
  }

  async function loadEligibility() {
    try {
      const payload = await fetchJson('/api/v1/flexibility/eligibility?year=2025');
      const rows = asArray(payload);
      if (rows.length === 0) return;
      const map: Record<string, { canBank: boolean; canBorrow: boolean; canPool: boolean; hasPoolMembership: boolean; hasBorrowThisYear: boolean }> = {};
      rows.forEach((row: any) => {
        if (!row?.vesselId) return;
        map[row.vesselId] = {
          canBank: Boolean(row.canBank),
          canBorrow: Boolean(row.canBorrow),
          canPool: Boolean(row.canPool),
          hasPoolMembership: Boolean(row.hasPoolMembership),
          hasBorrowThisYear: Boolean(row.hasBorrowThisYear)
        };
      });
      setEligibilityByVesselId(map);
      setVessels((prev) => {
        if (prev.length > 0) return prev;
        return rows.map((r: any) => ({ id: r.vesselId, name: r.name ?? 'Unnamed Vessel', imoNumber: r.imoNumber ?? '' }));
      });
      if (!selectedVessel && rows.length > 0) setSelectedVessel(rows[0].vesselId);
    } catch {
      console.error('Eligibility API unavailable.');
    }
  }

  async function loadMarketQuote() {
    try {
      const q = await fetchJson('/api/v1/marketplace/cb-rate?year=2025');
      if (!q) return;
      setMarketQuote({
        priceEurPerCb: Number(q.priceEurPerCb ?? 0),
        source: q.source ?? 'fallback-static',
        symbol: q.symbol ?? 'MCO2'
      });
    } catch {
      console.error('Marketplace quote unavailable.');
    }
  }

  async function loadStrategy() {
    try {
      const payload = await fetchJson('/api/v1/flexibility/strategy/recommendations?year=2025');
      const rows = asArray(payload);
      if (rows.length === 0) return;
      const map: Record<string, StrategyRow> = {};
      rows.forEach((r: StrategyRow) => {
        if (!r?.vesselId) return;
        map[r.vesselId] = r;
      });
      setStrategyByVesselId(map);
    } catch {
      console.error('Strategy API unavailable.');
    }
  }

  const handleExecute = async (action: string) => {
    if (vessels.length === 0) {
      toast.error("No Vessel Available", { description: "Register at least one vessel before executing flexibility actions." });
      return;
    }

    if (vessels.length > 0 && !selectedVessel) {
       toast.error("Invalid Configuration", { description: "Please explicitly bind a Vessel target to this transaction." });
       return;
    }

    const selectedLedger = ledgerByVesselId[selectedVessel];
    const selectedEligibility = eligibilityByVesselId[selectedVessel];
    const parsedAmount = Number(amount);
    if (Number.isNaN(parsedAmount) || parsedAmount <= 0) {
      toast.error("Invalid Amount", { description: "Enter a positive amount before committing." });
      return;
    }

    if (selectedLedger) {
      const borrowingCap = selectedLedger.targetIntensity * selectedLedger.energyInScope * 0.02;
      if (action === 'bank' && selectedLedger.icb <= 0) {
        toast.error("Banking Blocked", { description: "Selected vessel has no positive compliance balance to bank." });
        return;
      }
      if (action === 'borrow' && selectedLedger.icb >= 0) {
        toast.error("Borrowing Blocked", { description: "Selected vessel is not in deficit." });
        return;
      }
      if (action === 'borrow' && parsedAmount > borrowingCap) {
        toast.error("Borrowing Cap Exceeded", { description: `Amount exceeds vessel cap (${borrowingCap.toFixed(2)} gCO2eq).` });
        return;
      }
    }

    if (selectedEligibility) {
      if (action === 'borrow' && selectedEligibility.hasPoolMembership) {
        toast.error("Borrowing Blocked", { description: "This vessel already participates in a pool for 2025; pick a non-pooled deficit vessel." });
        return;
      }
      if (action === 'pool' && selectedEligibility.hasBorrowThisYear) {
        toast.error("Pooling Blocked", { description: "This vessel already borrowed in 2025; pick a non-borrowed surplus vessel." });
        return;
      }
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
          description: data?.message ?? `The ${action.toUpperCase()} action for ${amount} gCO2eq was synchronized to PostgreSQL correctly.`
        });
        await loadEligibility();
        await loadStrategy();
        await loadPools();
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

  const selectedLedger = selectedVessel ? ledgerByVesselId[selectedVessel] : undefined;
  const selectedEligibility = selectedVessel ? eligibilityByVesselId[selectedVessel] : undefined;
  const borrowingCapHint = selectedLedger ? selectedLedger.targetIntensity * selectedLedger.energyInScope * 0.02 : null;
  const modalVessels = activeModal ? vessels.filter(v => isEligibleForAction(v.id, activeModal)) : vessels;
  const advisoryRows = vessels
    .map(v => ({
      ...v,
      eligibility: eligibilityByVesselId[v.id],
      ledger: ledgerByVesselId[v.id]
    }))
    .sort((a, b) => Number(a.ledger?.icb ?? 0) - Number(b.ledger?.icb ?? 0));
  const sourceCandidates = vessels.filter(v => eligibilityByVesselId[v.id]?.canPool);
  const targetCandidates = vessels.filter(v => eligibilityByVesselId[v.id]?.canBorrow);
  const selectedSourceBalance = poolSourceVessel ? Number(eligibilityByVesselId[poolSourceVessel]?.canPool ? (advisoryRows.find(r => r.id === poolSourceVessel)?.ledger?.icb ?? 0) : 0) : 0;
  const selectedTargetDeficitAbs = poolTargetVessel ? Math.abs(Number(advisoryRows.find(r => r.id === poolTargetVessel)?.ledger?.icb ?? 0)) : 0;
  const suggestedPoolAmount = Math.max(0, Math.min(selectedSourceBalance > 0 ? selectedSourceBalance : 0, selectedTargetDeficitAbs));

  function isEligibleForAction(vesselId: string, action: 'bank' | 'borrow' | 'pool') {
    const e = eligibilityByVesselId[vesselId];
    if (!e) return true;
    if (action === 'bank') return e.canBank;
    if (action === 'borrow') return e.canBorrow;
    return e.canPool;
  }

  function getBlockReason(action: 'bank' | 'borrow' | 'pool', vesselId: string, amountValue: string) {
    const ledger = ledgerByVesselId[vesselId];
    const eligibility = eligibilityByVesselId[vesselId];
    const parsedAmount = Number(amountValue);

    if (!ledger || !eligibility) return null;
    if (Number.isNaN(parsedAmount) || parsedAmount <= 0) {
      return "Blocked: Enter a positive transaction amount.";
    }

    if (action === 'bank') {
      if (ledger.icb <= 0) return "Blocked: Vessel has non-positive compliance balance, so it cannot bank surplus.";
      if (!eligibility.canBank) return "Blocked: Vessel already has a bank record for 2025 or is otherwise restricted.";
      return null;
    }
    if (action === 'borrow') {
      const cap = ledger.targetIntensity * ledger.energyInScope * 0.02;
      if (ledger.icb >= 0) return "Blocked: Vessel is not in deficit, so borrowing is not allowed.";
      if (eligibility.hasPoolMembership) return "Blocked because this vessel is already pooled in 2025 (pool and borrow are mutually exclusive per vessel-year).";
      if (parsedAmount > cap) return `Blocked: Requested amount exceeds borrowing cap (${cap.toFixed(2)} gCO2eq).`;
      if (!eligibility.canBorrow) return "Blocked: Vessel already borrowed this year or is restricted by policy.";
      return null;
    }

    if (eligibility.hasBorrowThisYear) return "Blocked because this vessel already borrowed in 2025 (pool and borrow are mutually exclusive per vessel-year).";
    if (ledger.icb <= 0) return "Blocked: Pool entry in this MVP flow requires positive pre-allocation balance.";
    if (!eligibility.canPool) return "Blocked: Vessel is already in a pool or restricted this period.";
    return null;
  }

  async function handleBuyCbs() {
    if (!selectedVessel) {
      toast.error("Select Vessel", { description: "Choose a vessel before buying CBs." });
      return;
    }
    const amountCb = Number(buyAmount);
    if (Number.isNaN(amountCb) || amountCb <= 0) {
      toast.error("Invalid Amount", { description: `CB buy amount must be positive ${UNITS.cb} (${UNITS.cbEquivalent}).` });
      return;
    }
    try {
      const res = await fetch(apiUrl('/api/v1/marketplace/purchase'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ vesselId: selectedVessel, amountCb, year: 2025 })
      });
      const payload = await res.json().catch(() => null);
      if (!res.ok) {
        toast.error("Marketplace Buy Failed", { description: payload?.message ?? `HTTP ${res.status}` });
        return;
      }
      toast.success("CB Purchase Recorded", {
        description: `Bought ${payload?.amountCb ?? amountCb} CB at market-linked rate.`
      });
      await loadEligibility();
      await loadStrategy();
    } catch {
      toast.error("Marketplace Offline", { description: "Unable to execute CB buy now." });
    }
  }

  async function loadPools() {
    try {
      const payload = await fetchJson('/api/v1/flexibility/pools?year=2025');
      const rows = asArray<PoolRow>(payload);
      setPoolRows(rows);
    } catch {
      setPoolRows([]);
    }
  }

  async function handleCreatePoolTransfer() {
    if (!poolSourceVessel || !poolTargetVessel) {
      toast.error('Select Vessels', { description: 'Choose both source and target vessels.' });
      return;
    }
    if (poolSourceVessel === poolTargetVessel) {
      toast.error('Invalid Pair', { description: 'Source and target vessels must be different.' });
      return;
    }
    const amount = Number(poolAmount);
    if (Number.isNaN(amount) || amount <= 0) {
      toast.error('Invalid Amount', { description: 'Pool amount must be positive.' });
      return;
    }
    const res = await fetch(apiUrl('/api/v1/flexibility/pools'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sourceVesselId: poolSourceVessel, targetVesselId: poolTargetVessel, amount, year: 2025 })
    });
    const payload = await res.json().catch(() => null);
    if (!res.ok) {
      toast.error('Pool Create Blocked', { description: payload?.message ?? `HTTP ${res.status}` });
      return;
    }
    toast.success('Pool Transfer Created', { description: payload?.message ?? 'Pool created successfully.' });
    await loadPools();
    await loadEligibility();
    await loadStrategy();
  }

  async function handleEditPoolTransfer() {
    if (!editingPoolId) {
      toast.error('Select Pool', { description: 'Pick a pool row to edit.' });
      return;
    }
    const amount = Number(editPoolAmount);
    if (Number.isNaN(amount) || amount <= 0) {
      toast.error('Invalid Amount', { description: 'New amount must be positive.' });
      return;
    }
    const res = await fetch(apiUrl(`/api/v1/flexibility/pools/${editingPoolId}`), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ amount })
    });
    const payload = await res.json().catch(() => null);
    if (!res.ok) {
      toast.error('Pool Edit Failed', { description: payload?.message ?? `HTTP ${res.status}` });
      return;
    }
    toast.success('Pool Updated', { description: payload?.message ?? 'Transfer amount updated.' });
    await loadPools();
  }

  async function handleAddAllocation() {
    if (!selectedPoolId || !allocationVesselId) {
      toast.error('Select Pool and Vessel', { description: 'Choose a pool and vessel before adding allocation.' });
      return;
    }
    const transfer = Number(allocationTransfer);
    if (Number.isNaN(transfer) || transfer === 0) {
      toast.error('Invalid Transfer', { description: 'Transfer must be non-zero.' });
      return;
    }
    const res = await fetch(apiUrl(`/api/v1/flexibility/pools/${selectedPoolId}/allocations`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ vesselId: allocationVesselId, transfer })
    });
    const payload = await res.json().catch(() => null);
    if (!res.ok) {
      toast.error('Add Allocation Failed', { description: payload?.message ?? `HTTP ${res.status}` });
      return;
    }
    toast.success('Allocation Added', { description: payload?.message ?? 'Participant allocation added.' });
    await loadPools();
    await loadEligibility();
    await loadStrategy();
  }

  async function handleRemoveParticipant(poolId: string, vesselId: string) {
    const res = await fetch(apiUrl(`/api/v1/flexibility/pools/${poolId}/participants/${vesselId}`), { method: 'DELETE' });
    const payload = await res.json().catch(() => null);
    if (!res.ok) {
      toast.error('Remove Participant Failed', { description: payload?.message ?? `HTTP ${res.status}` });
      return;
    }
    toast.success('Participant Removed', { description: payload?.message ?? 'Removed from pool.' });
    await loadPools();
    await loadEligibility();
    await loadStrategy();
  }

  async function handleCancelPool(poolId: string) {
    const res = await fetch(apiUrl(`/api/v1/flexibility/pools/${poolId}/cancel`), { method: 'POST' });
    const payload = await res.json().catch(() => null);
    if (!res.ok) {
      toast.error('Cancel Pool Failed', { description: payload?.message ?? `HTTP ${res.status}` });
      return;
    }
    toast.success('Pool Cancelled', { description: payload?.message ?? 'Pool cancelled.' });
    await loadPools();
    await loadEligibility();
    await loadStrategy();
  }

  async function handleDeletePool(poolId: string) {
    const res = await fetch(apiUrl(`/api/v1/flexibility/pools/${poolId}`), { method: 'DELETE' });
    const payload = await res.json().catch(() => null);
    if (!res.ok) {
      toast.error('Delete Pool Failed', { description: payload?.message ?? `HTTP ${res.status}` });
      return;
    }
    toast.success('Pool Deleted', { description: payload?.message ?? 'Pool deleted.' });
    await loadPools();
    await loadEligibility();
    await loadStrategy();
  }

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

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-lg font-bold text-slate-900 mb-1">Vessel Exposure Advisory (2025)</h3>
          <p className="text-sm text-slate-500 mb-4">Action advice is vessel-specific. Pooling/borrowing mutual exclusivity is enforced per vessel-year.</p>
          <div className="space-y-2 max-h-64 overflow-auto pr-1">
            {advisoryRows.map((row) => {
              const e = row.eligibility;
              const icb = Number(row.ledger?.icb ?? 0);
              const strategy = strategyByVesselId[row.id];
              const advice = e?.canBorrow ? 'Borrow candidate' : e?.canBank ? 'Bank candidate' : e?.canPool ? 'Pool candidate' : 'Restricted';
              const tone = advice.includes('Borrow') ? 'text-red-700 bg-red-50 border-red-200'
                : advice.includes('Bank') ? 'text-emerald-700 bg-emerald-50 border-emerald-200'
                : advice.includes('Pool') ? 'text-blue-700 bg-blue-50 border-blue-200'
                : 'text-slate-700 bg-slate-50 border-slate-200';
              return (
                <div key={row.id} className="flex items-center justify-between border rounded-lg px-3 py-2">
                  <div>
                    <div className="font-semibold text-slate-900 text-sm">{row.name}</div>
                    <div className="text-xs text-slate-500">ICB: {icb.toFixed(2)} gCO2eq {e?.hasPoolMembership ? '• In Pool' : ''} {e?.hasBorrowThisYear ? '• Borrowed' : ''}</div>
                    {strategy && (
                      <div className="text-xs text-indigo-700 mt-1">
                        Best action: <b>{strategy.recommendation}</b> • Penalty EUR {Number(strategy.estimatedPenaltyCostEur ?? 0).toFixed(0)} • Buy EUR {Number(strategy.estimatedBuyCostEur ?? 0).toFixed(0)}
                      </div>
                    )}
                  </div>
                  <span className={`text-xs border px-2 py-1 rounded-full font-semibold ${tone}`}>{advice}</span>
                </div>
              );
            })}
          </div>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 className="text-lg font-bold text-slate-900 mb-1">CB Marketplace (Indicative)</h3>
          <p className="text-sm text-slate-500 mb-4">Buy compliance balance as CB units (1 CB = 1 tCO2eq in this marketplace) from FuelEU-relevant benchmark sources.</p>
          <div className="p-3 rounded-lg border border-blue-200 bg-blue-50 text-sm text-blue-900 mb-4">
            <div><b>Recent rate:</b> {marketQuote ? `EUR ${marketQuote.priceEurPerCb.toFixed(2)} per CB (${UNITS.cbEquivalent})` : 'Loading...'}</div>
            <div><b>Source:</b> {marketQuote ? `${marketQuote.source} (${marketQuote.symbol})` : 'n/a'}</div>
          </div>
          <div className="space-y-3">
            <div>
              <label className="text-sm font-semibold text-slate-700 block mb-1">Vessel</label>
              <select
                value={selectedVessel}
                onChange={(e) => setSelectedVessel(e.target.value)}
                className="w-full px-3 py-2 bg-slate-100 border border-slate-300 rounded-lg text-slate-900"
              >
                {vessels.map(v => <option key={v.id} value={v.id}>{v.name} (IMO: {v.imoNumber || v.id})</option>)}
              </select>
            </div>
            <div>
              <label className="text-sm font-semibold text-slate-700 block mb-1">Buy Amount (CB)</label>
              <input
                type="number"
                value={buyAmount}
                onChange={(e) => setBuyAmount(e.target.value)}
                className="w-full px-3 py-2 bg-white border border-slate-300 rounded-lg text-slate-900"
              />
            </div>
            <button onClick={handleBuyCbs} className="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-2.5 rounded-xl">
              Buy CBs from Marketplace
            </button>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <h3 className="text-lg font-bold text-slate-900 mb-1">Multi-Vessel Pool Builder</h3>
          <p className="text-sm text-slate-500 mb-4">FuelEU-style transfer bounds are enforced per vessel and net pool cannot become negative.</p>
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-3 mb-4">
          <div>
            <label className="text-xs font-semibold text-slate-700 block mb-1">Source (surplus)</label>
            <select value={poolSourceVessel} onChange={(e) => setPoolSourceVessel(e.target.value)} className="w-full px-3 py-2 border rounded-lg bg-slate-50">
              <option value="">Select source</option>
              {sourceCandidates.map(v => <option key={v.id} value={v.id}>{v.name}</option>)}
            </select>
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-700 block mb-1">Target (deficit)</label>
            <select value={poolTargetVessel} onChange={(e) => setPoolTargetVessel(e.target.value)} className="w-full px-3 py-2 border rounded-lg bg-slate-50">
              <option value="">Select target</option>
              {targetCandidates.map(v => <option key={v.id} value={v.id}>{v.name}</option>)}
            </select>
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-700 block mb-1">Transfer (gCO2eq)</label>
            <input type="number" value={poolAmount} onChange={(e) => setPoolAmount(e.target.value)} className="w-full px-3 py-2 border rounded-lg" />
          </div>
          <div className="flex items-end">
            <button onClick={handleCreatePoolTransfer} className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2.5 rounded-xl">Create Pool Transfer</button>
          </div>
        </div>
          <div className="mb-4 text-xs bg-slate-50 border border-slate-200 rounded-lg p-3 text-slate-700 flex items-center justify-between">
            <span>
              Suggested transfer: <b>{suggestedPoolAmount.toFixed(2)} gCO2eq</b> (min of source surplus and target deficit)
            </span>
            <button
              onClick={() => setPoolAmount(suggestedPoolAmount > 0 ? suggestedPoolAmount.toFixed(2) : '0')}
              className="text-blue-700 underline font-semibold"
            >
              Use Suggested
            </button>
          </div>

        <div className="border rounded-lg overflow-hidden">
          <div className="grid grid-cols-6 gap-2 px-3 py-2 bg-slate-50 text-xs font-semibold text-slate-600">
            <div>Name</div><div>Status</div><div>Participants</div><div>Net</div><div>Actions</div><div>Expand</div>
          </div>
          {poolRows.length === 0 ? (
            <div className="px-3 py-4 text-sm text-slate-500">No pools yet for 2025.</div>
          ) : poolRows.map((p) => (
            <div key={p.poolId} className="border-t">
              <div className="grid grid-cols-6 gap-2 px-3 py-2 text-sm items-center">
                <div>{p.name}</div>
                <div>{p.status}</div>
                <div>{p.participants?.length ?? 0}</div>
                <div>{Number(p.netBalance ?? 0).toFixed(2)} gCO2eq</div>
                <div className="space-x-2">
                  <button onClick={() => { setEditingPoolId(p.poolId); setEditPoolAmount(String(Math.abs(Number((p.participants || []).find((x) => Number(x.transfer) > 0)?.transfer ?? 0)))); }} className="text-blue-700 underline">Edit</button>
                  <button onClick={() => handleCancelPool(p.poolId)} className="text-amber-700 underline">Cancel</button>
                  <button onClick={() => handleDeletePool(p.poolId)} className="text-red-700 underline">Delete</button>
                </div>
                <button onClick={() => setExpandedPoolId(expandedPoolId === p.poolId ? '' : p.poolId)} className="text-indigo-700 underline">
                  {expandedPoolId === p.poolId ? 'Hide' : 'Show'}
                </button>
              </div>
              {expandedPoolId === p.poolId && (
                <div className="px-3 pb-3">
                  <div className="rounded-md border bg-slate-50">
                    <div className="grid grid-cols-4 gap-2 px-3 py-2 text-xs font-semibold text-slate-600">
                      <div>Vessel</div><div>Transfer (gCO2eq)</div><div>Role</div><div>Action</div>
                    </div>
                    {(p.participants || []).map((pt) => (
                      <div key={pt.participantId} className="grid grid-cols-4 gap-2 px-3 py-2 border-t text-sm">
                        <div>{pt.name}</div>
                        <div>{Number(pt.transfer ?? 0).toFixed(2)}</div>
                        <div>{Number(pt.transfer) >= 0 ? 'Contributor' : 'Receiver'}</div>
                        <div><button onClick={() => handleRemoveParticipant(p.poolId, pt.vesselId)} className="text-red-700 underline">Remove</button></div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>

        <div className="mt-3 grid grid-cols-1 lg:grid-cols-3 gap-3">
          <input type="text" value={editingPoolId} readOnly placeholder="Selected pool ID" className="px-3 py-2 border rounded-lg bg-slate-100 text-slate-600" />
          <input type="number" value={editPoolAmount} onChange={(e) => setEditPoolAmount(e.target.value)} placeholder="New amount (gCO2eq)" className="px-3 py-2 border rounded-lg" />
          <button onClick={handleEditPoolTransfer} className="bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-2.5 rounded-xl">Save Pool Edit</button>
        </div>
        <div className="mt-3 grid grid-cols-1 lg:grid-cols-4 gap-3">
          <select value={selectedPoolId} onChange={(e) => setSelectedPoolId(e.target.value)} className="px-3 py-2 border rounded-lg bg-slate-50">
            <option value="">Select existing draft pool</option>
            {poolRows.filter((p) => p.status === 'DRAFT').map((p) => <option key={p.poolId} value={p.poolId}>{p.name}</option>)}
          </select>
          <select value={allocationVesselId} onChange={(e) => setAllocationVesselId(e.target.value)} className="px-3 py-2 border rounded-lg bg-slate-50">
            <option value="">Select vessel</option>
            {vessels.filter((v) => !eligibilityByVesselId[v.id]?.hasPoolMembership).map((v) => <option key={v.id} value={v.id}>{v.name}</option>)}
          </select>
          <input type="number" value={allocationTransfer} onChange={(e) => setAllocationTransfer(e.target.value)} placeholder="Transfer (+surplus / -deficit)" className="px-3 py-2 border rounded-lg" />
          <button onClick={handleAddAllocation} className="bg-slate-900 hover:bg-slate-800 text-white font-bold py-2.5 rounded-xl">Add Allocation</button>
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
              {activeModal === 'bank' && <p className="text-sm text-slate-600 mb-6">Input the verified surplus (gCO2eq) you wish to securely bank for the next verification period. Target vessel must be specified.</p>}
              {activeModal === 'borrow' && <p className="text-sm text-slate-600 mb-6">Select the specific vessel deficit to cover. Be aware that the required borrowing amount will mathematically increase by <b>10%</b> due to the statutory multiplier penalty.</p>}
              {activeModal === 'pool' && <p className="text-sm text-slate-600 mb-6">Initialize a new Compliance Pool allocation targeting the designated constituent.</p>}
              
              <div className="space-y-5">
                {modalVessels.length > 0 ? (
                  <div>
                    <label className="text-sm font-semibold text-slate-700 block mb-2">Target Vessel Selection</label>
                    <select 
                      value={selectedVessel} 
                      onChange={(e) => setSelectedVessel(e.target.value)}
                      className="w-full px-4 py-3 bg-slate-100 border border-slate-300 rounded-lg text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 font-medium"
                    >
                       {modalVessels.map(v => <option key={v.id} value={v.id}>{v.name} (IMO: {v.imoNumber || v.id})</option>)}
                    </select>
                  </div>
                ) : (
                  <div className="p-4 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-900">
                    No eligible vessels for this action in 2025 under current policy records.
                  </div>
                )}
                
                <div>
                  <label className="text-sm font-semibold text-slate-700 block mb-2">Simulation Mechanism Total (gCO2eq)</label>
                  <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} className="w-full px-4 py-3 text-lg font-mono bg-white border border-slate-300 rounded-lg text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
                {selectedLedger && (
                  <div className="text-xs bg-slate-50 border border-slate-200 rounded-lg p-3 text-slate-600 leading-relaxed">
                    <div><b>Selected ICB:</b> {selectedLedger.icb.toFixed(2)} gCO2eq</div>
                    {borrowingCapHint !== null && <div><b>Borrowing Cap (2% rule):</b> {borrowingCapHint.toFixed(2)} gCO2eq</div>}
                  </div>
                )}
                {selectedVessel && selectedLedger && selectedEligibility && (() => {
                  const reason = getBlockReason(activeModal, selectedVessel, amount);
                  if (!reason) {
                    return (
                      <div className="text-xs bg-emerald-50 border border-emerald-200 rounded-lg p-3 text-emerald-800">
                        Eligible: selected vessel can proceed for this action.
                      </div>
                    );
                  }
                  return (
                    <div className="text-xs bg-red-50 border border-red-200 rounded-lg p-3 text-red-800">
                      <b>Why blocked?</b> {reason}
                    </div>
                  );
                })()}
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
