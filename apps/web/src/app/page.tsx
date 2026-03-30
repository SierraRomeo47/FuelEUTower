"use client";

import React, { useState, useEffect } from 'react';
import { 
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer, Legend 
} from 'recharts';
import { AlertTriangle, ArrowUpRight, ArrowDownRight, MoreHorizontal } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { apiUrl } from '@/lib/api';

const fleetData = [
  { month: 'Jan', intensity: 89.1, target: 89.34 },
  { month: 'Feb', intensity: 89.5, target: 89.34 },
  { month: 'Mar', intensity: 88.9, target: 89.34 },
  { month: 'Apr', intensity: 88.2, target: 89.34 },
  { month: 'May', intensity: 89.0, target: 89.34 },
  { month: 'Jun', intensity: 89.4, target: 89.34 },
];

const initialVessels = [
  { id: 'IMO9434761', name: 'MV Baltic Horizon', type: 'Container', icb: -3180.40, status: 'Deficit' },
  { id: 'IMO9762214', name: 'MV Adriatic Pioneer', type: 'Ro-Ro', icb: +980.20, status: 'Compliant' },
  { id: 'IMO9385420', name: 'MT North Sea Progress', type: 'Tanker', icb: -1240.00, status: 'Deficit' },
  { id: 'IMO9521678', name: 'MV Iberian Link', type: 'Container', icb: +620.80, status: 'Compliant' },
  { id: 'IMO9677743', name: 'MV Celtic Trader', type: 'Bulker', icb: -1715.60, status: 'Deficit' },
];

type DocStatusRow = {
  imo: string;
  statusLabel: string;
  step: number;
};

export default function Dashboard() {
  const [mounted, setMounted] = useState(false);
  const [vesselList, setVesselList] = useState<any[]>(initialVessels);
  const [docStatusByImo, setDocStatusByImo] = useState<Record<string, DocStatusRow>>({});
  const [docCounts, setDocCounts] = useState({
    pending: 0,
    inReview: 0,
    verificationComplete: 0,
    issued: 0
  });
  const [kpiData, setKpiData] = useState({
    totalIcb: -4535.00,
    totalAcb: 1601.00,
    penaltyExposure: 142500,
    totalBorrowingCap: 17120.00
  });
  const router = useRouter();

  useEffect(() => {
    setMounted(true);
    const fetchDashboardData = async () => {
      try {
        const [vesselsRes, kpisRes, docRes] = await Promise.all([
          fetch(apiUrl('/api/v1/dashboard/vessels')).catch(() => null),
          fetch(apiUrl('/api/v1/dashboard/kpis')).catch(() => null),
          fetch(apiUrl('/api/v1/doc-tracker/statuses?year=2025')).catch(() => null)
        ]);
        
        if (vesselsRes && vesselsRes.ok) {
          const vData = await vesselsRes.json();
          if (vData && vData.length > 0) setVesselList(vData);
        }
        
        if (kpisRes && kpisRes.ok) {
          const kData = await kpisRes.json();
          setKpiData({
            totalIcb: kData.totalIcb ?? -5849.70,
            totalAcb: kData.totalAcb ?? 1760.70,
            penaltyExposure: kData.penaltyExposure ?? 142500,
            totalBorrowingCap: kData.totalBorrowingCap ?? 18400.00
          });
        }

        if (docRes && docRes.ok) {
          const docRows = await docRes.json();
          if (Array.isArray(docRows)) {
            const map: Record<string, DocStatusRow> = {};
            const counts = { pending: 0, inReview: 0, verificationComplete: 0, issued: 0 };
            docRows.forEach((row: any) => {
              const key = (row.imo ?? '').toString().toUpperCase();
              if (key) {
                map[key] = {
                  imo: key,
                  statusLabel: row.statusLabel ?? 'Missing Flexibility',
                  step: row.step ?? 0
                };
              }
              const status = (row.docStatus ?? '').toString().toUpperCase();
              if (status === 'PENDING_AUDITOR') counts.pending += 1;
              if (status === 'IN_REVIEW') counts.inReview += 1;
              if (status === 'VERIFICATION_COMPLETE') counts.verificationComplete += 1;
              if (status === 'DOC_ISSUED_FINAL') counts.issued += 1;
            });
            setDocStatusByImo(map);
            setDocCounts(counts);
          }
        }
      } catch (err) {
        console.error("API Integration Error:", err);
      }
    };
    fetchDashboardData();
  }, []);

  if (!mounted) return null; // Prevent Recharts hydration mismatch

  return (
    <div className="space-y-8 max-w-[1600px] mx-auto animate-in fade-in duration-700 pb-12">
      
      {/* Header */}
      <div className="flex flex-col gap-2">
        <h2 className="text-3xl font-bold tracking-tight text-slate-900">Executive Overview</h2>
        <p className="text-slate-500">Real-time compliance trajectories, aggregated fleet deficits, and explicit regulatory boundaries.</p>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <KPI 
          title="Initial Compliance Balance" 
          value={`${kpiData.totalIcb > 0 ? '+' : ''}${kpiData.totalIcb.toLocaleString('en-US', {minimumFractionDigits: 2})} MJ`} 
          subtext="Unverified raw trajectory"
          trend={kpiData.totalIcb > 0 ? "up" : "down"}
        />
        <KPI 
          title="Verified Balance (ACB)" 
          value={`${kpiData.totalAcb > 0 ? '+' : ''}${kpiData.totalAcb.toLocaleString('en-US', {minimumFractionDigits: 2})} MJ`} 
          subtext="Regulator locked surplus"
          trend={kpiData.totalAcb > 0 ? "up" : "down"}
          highlight
        />
        <KPI 
          title="Current Penalty Exposure" 
          value={`€ ${kpiData.penaltyExposure.toLocaleString('en-US')}`} 
          subtext="Estimated residual cost"
          trend="up"
          danger
        />
        <KPI 
          title="Fleet Borrowing Capacity" 
          value={`${kpiData.totalBorrowingCap.toLocaleString('en-US', {minimumFractionDigits: 2})} MJ`} 
          subtext="2% statutory cap limit"
          trend="down"
        />
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <DocCountCard label="Pending Auditor" value={docCounts.pending} tone="amber" />
        <DocCountCard label="In Review" value={docCounts.inReview} tone="blue" />
        <DocCountCard label="Verification Complete" value={docCounts.verificationComplete} tone="indigo" />
        <DocCountCard label="DoC Issued" value={docCounts.issued} tone="emerald" />
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Main Timeline Chart */}
        <div className="col-span-2 bg-white rounded-xl border border-slate-200 shadow-sm p-6 hover:shadow-md transition-shadow">
          <div className="flex justify-between items-center mb-6">
            <div>
              <h3 className="text-lg font-semibold text-slate-900">GHG Intensity Trajectory</h3>
              <p className="text-sm text-slate-500">Fleet average vs Legislative Threshold (89.34 gCO2eq/MJ)</p>
            </div>
          </div>
          <div className="h-[320px]">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={fleetData} margin={{ top: 10, right: 10, bottom: 5, left: -20 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                <XAxis dataKey="month" axisLine={false} tickLine={false} tick={{fill: '#64748b', fontSize: 13}} dy={10} />
                <YAxis domain={['dataMin - 0.5', 'dataMax + 0.5']} axisLine={false} tickLine={false} tick={{fill: '#64748b', fontSize: 13}} />
                <RechartsTooltip 
                  cursor={{stroke: '#cbd5e1', strokeWidth: 1, strokeDasharray: '5 5'}}
                  contentStyle={{ borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)' }}
                />
                <Legend iconType="circle" wrapperStyle={{paddingTop: '20px'}} />
                <Line type="monotone" dataKey="target" stroke="#ef4444" strokeWidth={2} strokeDasharray="5 5" name="Target Base Limit" dot={false} />
                <Line type="monotone" dataKey="intensity" stroke="#2563eb" strokeWidth={3} dot={{r: 5, strokeWidth: 2, fill: "white"}} activeDot={{r: 7}} name="Fleet Average (Weighted)" />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Mechanism Pie/Bar */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 flex flex-col hover:shadow-md transition-shadow">
          <h3 className="text-lg font-semibold text-slate-900 mb-6 border-b border-slate-100 pb-4">Flexibility Operations</h3>
          <div className="space-y-6 flex-1">
            <MechanismRow label="Banked Surplus (2025)" value="+0.00" total={Math.max(Math.abs(kpiData.totalAcb), 1).toFixed(2)} color="bg-emerald-500" />
            <MechanismRow label="Borrowed Amount (2025)" value="-800.00" total={Math.max(kpiData.totalBorrowingCap, 1).toFixed(2)} color="bg-amber-500" />
            <MechanismRow label="Sub-Fleet Pool Transfer (2025)" value="+1200.00" total={Math.max(Math.abs(kpiData.totalIcb), 1).toFixed(2)} color="bg-blue-500" />
          </div>
            
          <div className="mt-8 p-4 bg-amber-50 rounded-xl border border-amber-200 flex gap-3 items-start shadow-sm">
            <AlertTriangle className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
            <div className="text-sm text-amber-900 leading-relaxed">
              <span className="font-bold block mb-1">Consecutive Borrowing Block</span>
              <p className="text-amber-800">Borrowing chains are blocked in consecutive reporting years for the same vessel. Route deficit balancing through eligible pool transfers instead.</p>
            </div>
          </div>
        </div>
      </div>

      {/* Fleet Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden hover:shadow-md transition-shadow">
        <div className="px-6 py-5 border-b border-slate-200 flex justify-between items-center bg-slate-50/50">
          <div>
             <h3 className="text-lg font-semibold text-slate-900">High Risk Constituents</h3>
             <p className="text-sm text-slate-500">Vessels requiring explicit flexibility mechanism targeting.</p>
          </div>
          <button onClick={() => router.push('/fleet')} className="text-sm font-semibold text-blue-600 hover:text-blue-800 bg-blue-50 px-4 py-2 rounded-lg transition-colors active:scale-95">
            View Full Register
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="bg-slate-100/50 text-slate-500 font-semibold border-b border-slate-200">
              <tr>
                <th className="px-6 py-4">Vessel Name</th>
                <th className="px-6 py-4">IMO Number</th>
                <th className="px-6 py-4">Ship Type</th>
                <th className="px-6 py-4 text-right">ICB Extrapolation (MJ)</th>
                <th className="px-6 py-4">Regulatory Status</th>
                <th className="px-6 py-4">DoC Pipeline</th>
                <th className="px-6 py-4"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {vesselList.map((v) => (
                <tr key={v.id} className="hover:bg-blue-50/50 transition-colors group cursor-pointer">
                  <td className="px-6 py-4 font-semibold text-slate-900">{v.name}</td>
                  <td className="px-6 py-4 text-slate-500 font-mono text-xs">{v.id}</td>
                  <td className="px-6 py-4 text-slate-500">{v.type}</td>
                  <td className={`px-6 py-4 text-right font-bold ${v.icb < 0 ? 'text-red-600' : 'text-emerald-600'}`}>
                    {v.icb > 0 ? '+' : ''}{v.icb.toLocaleString('en-US', {minimumFractionDigits: 2})}
                  </td>
                  <td className="px-6 py-4">
                    <span className={`px-3 py-1 rounded-full text-xs font-bold tracking-wide uppercase ${
                      v.status === 'Deficit' ? 'bg-red-100 text-red-700 border border-red-200' : 'bg-emerald-100 text-emerald-700 border border-emerald-200'
                    }`}>
                      {v.status}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    {(() => {
                      const doc = docStatusByImo[(v.id ?? '').toString().toUpperCase()];
                      const label = doc?.statusLabel ?? 'Missing Flexibility';
                      const step = doc?.step ?? 0;
                      const tone = step >= 4
                        ? 'bg-emerald-100 text-emerald-700 border-emerald-200'
                        : step >= 2
                          ? 'bg-blue-100 text-blue-700 border-blue-200'
                          : 'bg-slate-100 text-slate-600 border-slate-200';
                      return (
                        <span className={`px-3 py-1 rounded-full text-xs font-semibold border ${tone}`}>
                          {label}
                        </span>
                      );
                    })()}
                  </td>
                  <td className="px-6 py-4 text-slate-300 group-hover:text-blue-600 text-right transition-colors">
                    <MoreHorizontal className="w-5 h-5 ml-auto" />
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

function DocCountCard({ label, value, tone }: { label: string; value: number; tone: 'amber' | 'blue' | 'indigo' | 'emerald' }) {
  const toneClass =
    tone === 'amber'
      ? 'border-amber-200 bg-amber-50 text-amber-800'
      : tone === 'blue'
        ? 'border-blue-200 bg-blue-50 text-blue-800'
        : tone === 'indigo'
          ? 'border-indigo-200 bg-indigo-50 text-indigo-800'
          : 'border-emerald-200 bg-emerald-50 text-emerald-800';

  return (
    <div className={`rounded-xl border p-4 ${toneClass}`}>
      <div className="text-xs font-semibold uppercase tracking-wide">{label}</div>
      <div className="text-2xl font-extrabold mt-1">{value}</div>
    </div>
  );
}

function KPI({ title, value, subtext, trend, highlight, danger }: any) {
  return (
    <div className={`rounded-xl border p-6 shadow-sm flex flex-col justify-between transition-all duration-300 hover:-translate-y-1 hover:shadow-lg ${highlight ? 'bg-gradient-to-br from-emerald-50 to-white border-emerald-200' : danger ? 'bg-gradient-to-br from-red-50 to-white border-red-200' : 'bg-white border-slate-200'}`}>
      <h3 className={`text-sm font-semibold tracking-wide uppercase ${highlight ? 'text-emerald-800' : danger ? 'text-red-800' : 'text-slate-500'}`}>{title}</h3>
      <div className="mt-4 flex items-end justify-between">
        <div className={`text-3xl font-extrabold tracking-tight ${highlight ? 'text-emerald-700' : danger ? 'text-red-700' : 'text-slate-900'}`}>
          {value}
        </div>
        {trend === 'up' && <ArrowUpRight className={`w-6 h-6 mb-1 ${highlight ? 'text-emerald-600' : danger ? 'text-red-500' : 'text-slate-400'}`} />}
        {trend === 'down' && <ArrowDownRight className={`w-6 h-6 mb-1 ${highlight ? 'text-emerald-600' : 'text-red-500'}`} />}
      </div>
      <p className={`text-sm mt-3 font-medium ${highlight ? 'text-emerald-600/80' : danger ? 'text-red-600/80' : 'text-slate-400'}`}>
        {subtext}
      </p>
    </div>
  );
}

function MechanismRow({ label, value, total, color }: any) {
  const percentage = Math.min(100, (Math.abs(parseFloat(value)) / parseFloat(total)) * 100);
  return (
    <div>
      <div className="flex justify-between text-sm mb-2">
        <span className="font-semibold text-slate-700">{label}</span>
        <span className="text-slate-900 font-bold">{value} MJ</span>
      </div>
      <div className="h-2.5 w-full bg-slate-100 rounded-full overflow-hidden shadow-inner border border-slate-200/50">
        <div className={`h-full ${color} rounded-full transition-all duration-1000 ease-out`} style={{ width: `${percentage}%` }}></div>
      </div>
    </div>
  );
}
