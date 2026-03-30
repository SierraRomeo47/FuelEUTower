"use client";

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { 
  BarChart3, 
  Ship, 
  Settings, 
  UploadCloud, 
  Archive, 
  ShieldCheck, 
  Activity 
} from 'lucide-react';

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="w-64 bg-slate-900 text-slate-300 flex flex-col shadow-2xl z-20 shrink-0">
      <div className="h-16 flex items-center px-6 border-b border-slate-800 bg-slate-950">
        <h1 className="text-lg font-bold text-white flex items-center gap-2 tracking-tight">
          <Ship className="w-5 h-5 text-blue-500" />
          FuelEU <span className="font-light text-slate-400">Tower</span>
        </h1>
      </div>
      
      <nav className="flex-1 py-6 px-4 space-y-1 overflow-y-auto">
        <NavItem href="/" current={pathname} icon={<BarChart3 />} label="Executive Dashboard" />
        <NavItem href="/fleet" current={pathname} icon={<Ship />} label="Fleet Registry" />
        <NavItem href="/ledger" current={pathname} icon={<Activity />} label="Compliance Ledger" />
        <NavItem href="/flexibility" current={pathname} icon={<Archive />} label="Flexibility Controls" />
        <NavItem href="/doc-tracker" current={pathname} icon={<ShieldCheck />} label="DoC Tracker" />
        <NavItem href="/import" current={pathname} icon={<UploadCloud />} label="Data Import Center" />
      </nav>

      <div className="p-4 border-t border-slate-800">
        <NavItem href="/settings" current={pathname} icon={<Settings />} label="System Settings" />
        <div className="mt-4 flex items-center gap-3 px-2">
          <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white font-bold text-sm">
            SA
          </div>
          <div className="text-sm">
            <p className="font-medium text-slate-200">System Admin</p>
            <p className="text-xs text-slate-500">Acme Shipmanagement</p>
          </div>
        </div>
      </div>
    </aside>
  );
}

function NavItem({ icon, label, href, current }: { icon: React.ReactNode, label: string, href: string, current: string }) {
  const active = current === href || (href !== "/" && current.startsWith(href));
  return (
    <Link href={href} className={`flex items-center gap-3 px-3 py-2.5 rounded-lg font-medium text-sm transition-all duration-200 ${active ? 'bg-blue-600 text-white shadow-md' : 'hover:bg-slate-800 hover:text-white'}`}>
      <span className="w-5 h-5 opacity-80">{icon}</span>
      {label}
    </Link>
  );
}
