import React, { useState } from 'react';
import {
  exportPortableDataJson,
  importPortableDataJson,
  resetAllData
} from '../../services/db';
import { Download, Upload, RotateCcw, ShieldCheck, Database, FileText, Check, AlertCircle } from 'lucide-react';

export const SettingsScreen: React.FC = () => {
  const [importStatus, setImportStatus] = useState<{ success?: boolean; message?: string } | null>(null);
  const [showResetModal, setShowResetModal] = useState(false);
  const [importJsonText, setImportJsonText] = useState('');

  const handleExport = () => {
    const jsonStr = exportPortableDataJson();
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `stitchbook_backup_${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const handleImportFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result as string;
      if (content) {
        const ok = importPortableDataJson(content);
        if (ok) {
          setImportStatus({ success: true, message: 'Portable Stitchbook backup restored successfully!' });
          setTimeout(() => window.location.reload(), 1200);
        } else {
          setImportStatus({ success: false, message: 'Invalid JSON backup format.' });
        }
      }
    };
    reader.readAsText(file);
  };

  const handleImportText = () => {
    if (!importJsonText.trim()) return;
    const ok = importPortableDataJson(importJsonText);
    if (ok) {
      setImportStatus({ success: true, message: 'Portable Stitchbook backup restored successfully!' });
      setTimeout(() => window.location.reload(), 1200);
    } else {
      setImportStatus({ success: false, message: 'Invalid JSON backup format.' });
    }
  };

  const handleResetData = () => {
    resetAllData();
    window.location.reload();
  };

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-8 py-8 space-y-8 animate-fade-in">
      
      {/* Header */}
      <div>
        <h1 className="text-2xl sm:text-3xl font-serif-display font-bold text-stone-900">Settings & Data Ownership</h1>
        <p className="text-sm text-stone-500 mt-1">Manage portable exports, local data backups, and application status</p>
      </div>

      {/* Local-First Principles Banner */}
      <div className="bg-stone-900 text-stone-100 rounded-3xl p-6 sm:p-8 space-y-4 shadow-md">
        <div className="flex items-center gap-3 text-rose-400">
          <ShieldCheck className="w-6 h-6" />
          <h2 className="text-lg font-serif-display font-bold text-white">Private & Local-First Guardrails</h2>
        </div>
        <p className="text-xs sm:text-sm text-stone-300 leading-relaxed">
          Stitchbook stores all your project notes, custom pattern guides, execution states, and stash records directly inside your local browser database. Your craft records belong to you and work 100% offline without mandatory accounts or subscriptions.
        </p>
      </div>

      {/* Export & Import Section */}
      <div className="bg-white rounded-3xl p-6 sm:p-8 border border-stone-200 shadow-2xs space-y-6">
        <div className="flex items-center gap-3">
          <Database className="w-5 h-5 text-rose-800" />
          <div>
            <h2 className="text-lg font-serif-display font-bold text-stone-900">Portable Data Backup & Export</h2>
            <p className="text-xs text-stone-500">Download or restore complete JSON backup archives of your craft data</p>
          </div>
        </div>

        {importStatus && (
          <div className={`p-4 rounded-2xl flex items-center gap-3 text-xs font-semibold ${
            importStatus.success ? 'bg-emerald-50 text-emerald-900 border border-emerald-200' : 'bg-rose-50 text-rose-900 border border-rose-200'
          }`}>
            {importStatus.success ? <Check className="w-4 h-4 text-emerald-600" /> : <AlertCircle className="w-4 h-4 text-rose-600" />}
            <span>{importStatus.message}</span>
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
          
          {/* Export Box */}
          <div className="p-5 bg-stone-50 rounded-2xl border border-stone-200 space-y-3">
            <h3 className="text-sm font-semibold text-stone-900 flex items-center gap-2">
              <Download className="w-4 h-4 text-stone-700" /> Export Backup
            </h3>
            <p className="text-xs text-stone-500 leading-relaxed">
              Generate a portable JSON archive containing all projects, guides, drafts, stash items, and library bookmarks.
            </p>
            <button
              onClick={handleExport}
              className="w-full py-2.5 bg-stone-900 hover:bg-stone-800 text-white rounded-xl text-xs font-semibold transition-colors cursor-pointer"
            >
              Download JSON Backup
            </button>
          </div>

          {/* Import Box */}
          <div className="p-5 bg-stone-50 rounded-2xl border border-stone-200 space-y-3">
            <h3 className="text-sm font-semibold text-stone-900 flex items-center gap-2">
              <Upload className="w-4 h-4 text-stone-700" /> Restore Backup
            </h3>
            <p className="text-xs text-stone-500 leading-relaxed">
              Upload a previously exported `.json` backup file to restore your projects and guides.
            </p>
            <label className="block w-full py-2.5 bg-rose-800 hover:bg-rose-900 text-white text-center rounded-xl text-xs font-semibold transition-colors cursor-pointer">
              <span>Choose JSON File</span>
              <input
                type="file"
                accept=".json"
                onChange={handleImportFile}
                className="hidden"
              />
            </label>
          </div>

        </div>

        {/* Paste JSON Option */}
        <div className="pt-2 border-t border-stone-100 space-y-2">
          <label className="block text-xs font-semibold text-stone-700">Paste Backup JSON String Directly</label>
          <textarea
            rows={3}
            placeholder="Paste raw backup JSON text here..."
            value={importJsonText}
            onChange={e => setImportJsonText(e.target.value)}
            className="w-full p-3 bg-stone-50 border border-stone-200 rounded-xl text-xs font-mono focus:outline-none focus:ring-2 focus:ring-rose-800"
          />
          <button
            onClick={handleImportText}
            disabled={!importJsonText.trim()}
            className="px-4 py-2 bg-stone-800 hover:bg-stone-700 disabled:opacity-40 text-white rounded-xl text-xs font-semibold cursor-pointer"
          >
            Import JSON Text
          </button>
        </div>

      </div>

      {/* Danger Zone / Data Reset */}
      <div className="bg-white rounded-3xl p-6 sm:p-8 border border-rose-200 shadow-2xs space-y-4">
        <div>
          <h2 className="text-lg font-serif-display font-bold text-rose-900">Reset Application Data</h2>
          <p className="text-xs text-stone-500">Reset local database to initial sample state</p>
        </div>

        <button
          onClick={() => setShowResetModal(true)}
          className="flex items-center gap-2 px-4 py-2.5 bg-rose-50 hover:bg-rose-100 text-rose-800 border border-rose-200 rounded-xl text-xs font-semibold cursor-pointer"
        >
          <RotateCcw className="w-4 h-4" /> Reset Database
        </button>
      </div>

      {/* Reset Modal */}
      {showResetModal && (
        <div className="fixed inset-0 bg-stone-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl p-6 max-w-md w-full space-y-4 shadow-xl">
            <h3 className="text-lg font-serif-display font-bold text-rose-900">Confirm Database Reset?</h3>
            <p className="text-xs text-stone-600 leading-relaxed">
              This action will reset local storage and re-initialize Stitchbook with sample data. Be sure to export a backup if you wish to keep your records.
            </p>

            <div className="pt-3 flex items-center justify-end gap-2">
              <button
                onClick={() => setShowResetModal(false)}
                className="px-4 py-2 text-xs font-medium text-stone-600 hover:bg-stone-100 rounded-xl cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={handleResetData}
                className="px-4 py-2 bg-rose-700 hover:bg-rose-800 text-white rounded-xl text-xs font-medium cursor-pointer"
              >
                Reset Everything
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};
