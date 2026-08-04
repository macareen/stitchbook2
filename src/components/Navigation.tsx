import React from 'react';
import { NavigationDestination } from '../types';
import { Home, Folder, BookOpen, Layers, Settings, Play } from 'lucide-react';

interface NavigationProps {
  currentDestination: NavigationDestination;
  onNavigate: (dest: NavigationDestination) => void;
  activeExecutionGuideName?: string | null;
  onOpenActiveFocus?: () => void;
}

export const Navigation: React.FC<NavigationProps> = ({
  currentDestination,
  onNavigate,
  activeExecutionGuideName,
  onOpenActiveFocus
}) => {
  const items: { id: NavigationDestination; label: string; icon: React.ReactNode }[] = [
    { id: 'HOME', label: 'Home', icon: <Home className="w-5 h-5" /> },
    { id: 'PROJECTS', label: 'Projects', icon: <Folder className="w-5 h-5" /> },
    { id: 'LIBRARY', label: 'Library', icon: <BookOpen className="w-5 h-5" /> },
    { id: 'STASH', label: 'Stash', icon: <Layers className="w-5 h-5" /> },
    { id: 'SETTINGS', label: 'Settings', icon: <Settings className="w-5 h-5" /> },
  ];

  return (
    <>
      {/* Active Focus Session Floating Banner if executing */}
      {activeExecutionGuideName && (
        <div className="fixed bottom-16 md:bottom-6 right-4 z-40">
          <button
            onClick={onOpenActiveFocus}
            className="flex items-center gap-2.5 bg-rose-700 hover:bg-rose-800 text-white font-medium px-4 py-2.5 rounded-full shadow-lg transition-all animate-pulse hover:animate-none cursor-pointer"
          >
            <Play className="w-4 h-4 fill-white" />
            <span className="text-sm">Active Focus: <strong className="font-semibold">{activeExecutionGuideName}</strong></span>
          </button>
        </div>
      )}

      {/* Desktop Header Navigation */}
      <header className="hidden md:flex items-center justify-between px-8 py-4 bg-stone-100 border-b border-stone-200 sticky top-0 z-30 shadow-xs">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-stone-900 text-stone-50 flex items-center justify-center font-serif-display text-xl font-bold">
            S
          </div>
          <div>
            <h1 className="text-lg font-serif-display font-semibold text-stone-900 leading-tight">Stitchbook</h1>
            <p className="text-xs text-stone-500 font-sans">Craft Companion</p>
          </div>
        </div>

        <nav className="flex items-center gap-1 bg-stone-200/60 p-1 rounded-2xl border border-stone-300/50">
          {items.map((item) => {
            const isActive = currentDestination === item.id;
            return (
              <button
                key={item.id}
                onClick={() => onNavigate(item.id)}
                className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition-all cursor-pointer ${
                  isActive
                    ? 'bg-white text-stone-900 shadow-xs'
                    : 'text-stone-600 hover:text-stone-900 hover:bg-white/50'
                }`}
              >
                {item.icon}
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </header>

      {/* Mobile Bottom Navigation Bar */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 bg-stone-100/95 backdrop-blur-md border-t border-stone-200 px-2 py-1 z-30 flex items-center justify-around shadow-lg">
        {items.map((item) => {
          const isActive = currentDestination === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onNavigate(item.id)}
              className={`flex flex-col items-center justify-center py-1.5 px-3 rounded-xl transition-all cursor-pointer ${
                isActive ? 'text-rose-800 font-semibold' : 'text-stone-500 hover:text-stone-800'
              }`}
            >
              <div className={isActive ? 'scale-110 transition-transform' : ''}>
                {item.icon}
              </div>
              <span className="text-[11px] mt-0.5">{item.label}</span>
            </button>
          );
        })}
      </nav>
    </>
  );
};
