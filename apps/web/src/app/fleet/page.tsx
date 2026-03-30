"use client";

import React, { useState, useEffect } from 'react';
import { Search, Filter, Ship, MoreHorizontal, X } from 'lucide-react';
import { toast } from 'sonner';
import { apiUrl } from '@/lib/api';

const initialVessels = [
  { id: 'IMO9434761', imoNumber: 'IMO9434761', name: 'MV Baltic Horizon', vesselType: 'Container', buildYear: 2017, flagState: 'Cyprus', operator: 'Acme Shipmanagement', iceClass: '1A' },
  { id: 'IMO9762214', imoNumber: 'IMO9762214', name: 'MV Adriatic Pioneer', vesselType: 'Ro-Ro', buildYear: 2020, flagState: 'Italy', operator: 'Acme Shipmanagement', iceClass: '1B' },
  { id: 'IMO9385420', imoNumber: 'IMO9385420', name: 'MT North Sea Progress', vesselType: 'Tanker', buildYear: 2014, flagState: 'Malta', operator: 'Acme Shipmanagement', iceClass: '-' },
  { id: 'IMO9521678', imoNumber: 'IMO9521678', name: 'MV Iberian Link', vesselType: 'Container', buildYear: 2019, flagState: 'Portugal', operator: 'Acme Shipmanagement', iceClass: '1C' },
  { id: 'IMO9677743', imoNumber: 'IMO9677743', name: 'MV Celtic Trader', vesselType: 'Bulker', buildYear: 2016, flagState: 'Ireland', operator: 'Acme Shipmanagement', iceClass: '-' },
  { id: 'IMO9318042', imoNumber: 'IMO9318042', name: 'MV Aegean Relay', vesselType: 'Container', buildYear: 2013, flagState: 'Greece', operator: 'Acme Shipmanagement', iceClass: '1A Super' },
];

export default function FleetRegistry() {
  const [vessels, setVessels] = useState<any[]>(initialVessels);
  const [isModalOpen, setIsModalOpen] = useState(false);

  // New Vessel State
  const [newImo, setNewImo] = useState('');
  const [newName, setNewName] = useState('');
  const [newType, setNewType] = useState('Container');
  const [newIceClass, setNewIceClass] = useState('-');
  const [newYear, setNewYear] = useState('');
  const [newFlag, setNewFlag] = useState('');

  useEffect(() => {
    fetch(apiUrl('/api/v1/registry/vessels'))
      .then(r => r.json())
      .then(data => { if (data && data.length) setVessels(data); })
      .catch(e => console.error("API Integration Offline. Serving mocked initial state."));
  }, []);

  const handleFilter = () => {
    toast.info('Active Filters Applied', { description: 'Showing vessels matching your organization scope.' });
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const payload = {
      imoNumber: newImo,
      name: newName,
      vesselType: newType,
      iceClass: newIceClass,
      buildYear: parseInt(newYear) || null,
      flagState: newFlag
    };

    try {
      const res = await fetch(apiUrl('/api/v1/registry/vessels'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (res.ok) {
        const savedVessel = await res.json();
        setVessels([savedVessel, ...vessels]);
        toast.success('Vessel Registered', { description: `${savedVessel.name} (${savedVessel.imoNumber}) added dynamically via API.` });
      } else {
        const errorPayload = await res.json().catch(() => null);
        const existing = errorPayload && errorPayload.imoNumber ? errorPayload : null;
        if (res.status === 409 && existing) {
          toast.error('Duplicate IMO Number', {
            description: `${existing.name} (${existing.imoNumber}) already exists in the registry.`
          });
        } else {
          toast.error('Registration Failed', {
            description: errorPayload?.message ?? `API request rejected with HTTP ${res.status}.`
          });
        }
        return;
      }
    } catch(err) {
      toast.warning('Offline Mode', { description: 'Backend isolated. Creating mock entry for UI display.' });
      setVessels([{ id: newImo, operator: 'Acme Shipmanagement', ...payload }, ...vessels]);
    }
    
    setIsModalOpen(false);
    setNewImo(''); setNewName(''); setNewType('Container'); setNewIceClass('-'); setNewYear(''); setNewFlag('');
  };

  return (
    <div className="space-y-6 max-w-[1600px] mx-auto animate-in fade-in duration-500">
      
      <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-4">
        <div>
          <h2 className="text-3xl font-bold tracking-tight text-slate-900">Fleet Registry</h2>
          <p className="text-slate-500 mt-1">Master data module tracking vessels with mixed EU exposure assumptions for FuelEU scope simulations.</p>
        </div>
        <div className="flex gap-3">
          <button onClick={handleFilter} className="flex items-center gap-2 bg-white border border-slate-300 text-slate-700 px-4 py-2 rounded-lg font-medium hover:bg-slate-50 shadow-sm transition-colors">
            <Filter className="w-4 h-4" /> Filter
          </button>
          <button 
            onClick={() => setIsModalOpen(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white px-5 py-2 rounded-lg font-medium transition-colors shadow-sm"
          >
            + Register Vessel
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
        <div className="p-4 border-b border-slate-200 bg-slate-50 flex items-center gap-4">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input 
              type="text" 
              placeholder="Search by IMO or Vessel Name..." 
              className="w-full pl-9 pr-4 py-2 bg-white border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>
          <span className="text-sm text-slate-500 font-medium">Total: {vessels.length} Active Ships</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="bg-slate-50 text-slate-500 font-semibold border-b border-slate-200 uppercase text-xs tracking-wider">
              <tr>
                <th className="px-6 py-4">IMO Number</th>
                <th className="px-6 py-4">Vessel Name</th>
                <th className="px-6 py-4">Ship Type</th>
                <th className="px-6 py-4">Flag</th>
                <th className="px-6 py-4">Built</th>
                <th className="px-6 py-4">Ice Class</th>
                <th className="px-6 py-4"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {vessels.map((v) => (
                <tr key={v.id || v.imoNumber} className="hover:bg-blue-50/40 transition-colors group cursor-pointer">
                  <td className="px-6 py-4 text-slate-500 font-mono">{v.imoNumber || v.id}</td>
                  <td className="px-6 py-4 font-bold text-slate-900 flex items-center gap-3">
                    <Ship className="w-5 h-5 text-slate-400" />
                    {v.name}
                  </td>
                  <td className="px-6 py-4 text-slate-600">{v.vesselType || v.type}</td>
                  <td className="px-6 py-4 text-slate-600">{v.flagState || v.flag}</td>
                  <td className="px-6 py-4 text-slate-600">{v.buildYear || v.year}</td>
                  <td className="px-6 py-4 text-slate-600">{v.iceClass}</td>
                  <td className="px-6 py-4 text-slate-300 group-hover:text-blue-600 text-right">
                    <button onClick={() => toast("Vessel quick-actions opened.")}>
                      <MoreHorizontal className="w-5 h-5 ml-auto" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Registration Modal Overlay */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-md overflow-hidden animate-in zoom-in-95 duration-200">
            <div className="flex justify-between items-center p-6 border-b border-slate-100">
              <h3 className="text-xl font-bold text-slate-900">Register New Vessel</h3>
              <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-slate-600 transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleRegisterSubmit} className="p-6 space-y-4 max-h-[70vh] overflow-y-auto">
              <div className="space-y-2">
                <label className="text-sm font-semibold text-slate-700">IMO Number</label>
                <input required value={newImo} onChange={(e) => setNewImo(e.target.value)} type="text" placeholder="e.g. IMO9999999" className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-blue-500 focus:outline-none focus:ring-2" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-semibold text-slate-700">Vessel Name</label>
                <input required value={newName} onChange={(e) => setNewName(e.target.value)} type="text" placeholder="e.g. Ocean Pioneer" className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-blue-500 focus:outline-none focus:ring-2" />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-semibold text-slate-700">Ship Type</label>
                <select required value={newType} onChange={(e) => setNewType(e.target.value)} className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-blue-500 focus:outline-none focus:ring-2">
                  <option value="Container">Container</option>
                  <option value="Bulker">Bulker</option>
                  <option value="Tanker">Tanker</option>
                  <option value="Ro-Ro">Ro-Ro</option>
                  <option value="Passenger">Passenger</option>
                </select>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-slate-700">Year Built</label>
                  <input required value={newYear} onChange={(e) => setNewYear(e.target.value)} type="number" placeholder="e.g. 2024" className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-blue-500 focus:outline-none focus:ring-2" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-semibold text-slate-700">Ice Class</label>
                  <select required value={newIceClass} onChange={(e) => setNewIceClass(e.target.value)} className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-blue-500 focus:outline-none focus:ring-2">
                    <option value="-">None (-)</option>
                    <option value="1A Super">1A Super</option>
                    <option value="1A">1A</option>
                    <option value="1B">1B</option>
                    <option value="1C">1C</option>
                  </select>
                </div>
              </div>
              <div className="space-y-2">
                 <label className="text-sm font-semibold text-slate-700">Flag State Registration</label>
                 <input required value={newFlag} onChange={(e) => setNewFlag(e.target.value)} type="text" placeholder="e.g. Panama" className="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-blue-500 focus:outline-none focus:ring-2" />
              </div>
              <button type="submit" className="w-full mt-4 bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 rounded-lg transition-colors">
                Register to Fleet
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
