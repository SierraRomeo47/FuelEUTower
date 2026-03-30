import type { Metadata } from 'next';
import './globals.css';
import Sidebar from '@/components/Sidebar';
import Header from '@/components/Header';
import { Toaster } from 'sonner';

export const metadata: Metadata = {
  title: 'FuelEU Control Tower',
  description: 'Enterprise Maritime Compliance & Decarbonization Platform',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="h-screen flex bg-slate-50 text-slate-900 font-sans antialiased overflow-hidden">
        
        {/* Sidebar Navigation */}
        <Sidebar />

        {/* Main Workspace Area */}
        <div className="flex-1 flex flex-col min-w-0">
          <Header />
          
          <main className="flex-1 overflow-y-auto p-8 relative">
            {children}
          </main>
        </div>
        <Toaster position="top-right" expand={true} richColors />
      </body>
    </html>
  );
}
