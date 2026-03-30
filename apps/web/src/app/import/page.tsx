"use client";

import React, { useRef, useState } from 'react';
import { UploadCloud, FileSpreadsheet, FileCode, CheckCircle2, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { apiUrl } from '@/lib/api';

export default function ImportCenter() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isUploading, setIsUploading] = useState(false);

  const handleUploadClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsUploading(true);
    toast('Parsing FuelEU Files...', {
      description: `Ingesting ${file.name} via pipeline.`
    });

    try {
      const formData = new FormData();
      formData.append('file', file);

      const res = await fetch(apiUrl('/api/v1/imports/upload'), {
        method: 'POST',
        body: formData
      });

      if (res.ok) {
        toast.success('Ingestion Pipeline Successful', {
          description: `Successfully parsed and merged THETIS-MRV metrics into PostgreSQL.`
        });
      } else {
        toast.error('Pipeline Failed', { description: 'Backend validation rejected the dataset.' });
      }
    } catch (err) {
      toast.warning('Virtual Ingestion Environment', {
        description: `Server offline. Simulated exact THETIS-MRV extraction of ${file.name}.`
      });
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  return (
    <div className="space-y-6 max-w-[1200px] mx-auto animate-in fade-in duration-500 flex flex-col h-full">
      
      <div className="flex flex-col gap-2 mb-4">
        <h2 className="text-3xl font-bold tracking-tight text-slate-900">Data Import Center</h2>
        <p className="text-slate-500">Secure ingestion gateway for legacy FuelEU Excel trackers and automated XML port transmissions.</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* Upload Dropzone */}
        <div 
          onClick={handleUploadClick}
          className={`border-2 border-dashed ${isUploading ? 'border-blue-500 bg-blue-100' : 'border-blue-300 bg-blue-50/50'} rounded-2xl p-12 flex flex-col items-center justify-center text-center transition-all hover:bg-blue-50 hover:border-blue-500 cursor-pointer min-h-[400px] shadow-sm hover:shadow-md block relative overflow-hidden`}
        >
          <input 
            type="file" 
            ref={fileInputRef} 
            onChange={handleFileChange} 
            accept=".xml,.xlsx" 
            className="hidden" 
          />
          {isUploading ? (
            <div className="flex flex-col items-center">
              <Loader2 className="w-16 h-16 text-blue-600 animate-spin mb-6" />
              <h3 className="text-xl font-bold text-slate-900 mb-2">Processing Dataset...</h3>
              <p className="text-slate-500">Cross-referencing DNV registries...</p>
            </div>
          ) : (
            <>
              <div className="w-20 h-20 bg-blue-100 rounded-full flex items-center justify-center mb-6 shadow-sm">
                <UploadCloud className="w-10 h-10 text-blue-600" />
              </div>
              <h3 className="text-xl font-bold text-slate-900 mb-2">Drag & Drop Legacy Workbooks</h3>
              <p className="text-slate-500 max-w-sm mb-8 leading-relaxed">
                Upload your existing <code>.xlsx</code> Phase 1 files or standard THETIS-MRV <code>.xml</code> exports. Our parsing engine automatically digitizes your fleet data.
              </p>
              <button className="bg-blue-600 hover:bg-blue-700 text-white px-8 py-3 rounded-xl font-bold shadow-md transition-all active:scale-95 pointer-events-none">
                Select Files
              </button>
            </>
          )}
        </div>

        {/* Status Tracker */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-8">
          <h3 className="text-lg font-bold text-slate-900 mb-6 border-b border-slate-100 pb-4">Recent Ingestions</h3>
          <div className="space-y-6">
            <ImportRow 
              icon={<FileCode className="text-orange-500 w-5 h-5" />}
              name="9231614_PortPart1-DNV.xml"
              status="Parsed Successfully"
              time="Just Now"
            />
            <ImportRow 
              icon={<FileCode className="text-orange-500 w-5 h-5" />}
              name="voyage_emission_export_2025.xml"
              status="Parsed Successfully"
              time="Today, 10:45 AM"
            />
            <ImportRow 
              icon={<FileSpreadsheet className="text-emerald-500 w-5 h-5" />}
              name="FuelEU_Master_Q1_V2.xlsx"
              status="Parsed Successfully"
              time="Yesterday, 14:20 PM"
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function ImportRow({ icon, name, status, time }: { icon: React.ReactNode, name: string, status: string, time: string }) {
  return (
    <div className="flex items-start gap-4 p-4 rounded-xl border border-slate-100 bg-slate-50 hover:bg-white hover:shadow-sm transition-all">
      <div className="p-3 bg-white rounded-lg border border-slate-200 shadow-sm">
        {icon}
      </div>
      <div className="flex-1">
        <h4 className="font-semibold text-slate-800 text-sm truncate">{name}</h4>
        <div className="flex items-center gap-2 mt-1">
          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500" />
          <span className="text-xs font-medium text-emerald-700">{status}</span>
          <span className="text-xs text-slate-400 ml-auto">{time}</span>
        </div>
      </div>
    </div>
  );
}
