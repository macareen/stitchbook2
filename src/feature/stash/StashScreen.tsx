import React, { useState } from 'react';
import { StashItem, StashCategory } from '../../types';
import { getStashItems, saveStashItem, deleteStashItem } from '../../services/db';
import { Plus, Search, Filter, Layers, Edit3, Trash2, Package } from 'lucide-react';

export const StashScreen: React.FC = () => {
  const [stash, setStash] = useState<StashItem[]>(() => getStashItems());
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');

  const [editingItem, setEditingItem] = useState<Partial<StashItem> | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const refreshStash = () => {
    setStash(getStashItems());
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingItem || !editingItem.name) return;

    saveStashItem({
      id: editingItem.id,
      name: editingItem.name,
      category: editingItem.category || StashCategory.YARN,
      brand: editingItem.brand,
      colorway: editingItem.colorway,
      dyeLot: editingItem.dyeLot,
      weightCategory: editingItem.weightCategory,
      fiberContent: editingItem.fiberContent,
      quantity: editingItem.quantity ?? 1,
      unitLabel: editingItem.unitLabel || 'skeins',
      yardagePerUnit: editingItem.yardagePerUnit,
      notes: editingItem.notes
    });

    setIsModalOpen(false);
    setEditingItem(null);
    refreshStash();
  };

  const handleDelete = (id: string) => {
    deleteStashItem(id);
    refreshStash();
  };

  const filteredStash = stash.filter(item => {
    const matchesSearch = item.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (item.brand && item.brand.toLowerCase().includes(searchTerm.toLowerCase())) ||
      (item.colorway && item.colorway.toLowerCase().includes(searchTerm.toLowerCase()));
    const matchesCat = selectedCategory === 'ALL' || item.category === selectedCategory;

    return matchesSearch && matchesCat;
  });

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-8 py-8 space-y-8 animate-fade-in">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-serif-display font-bold text-stone-900">Yarn & Tools Stash</h1>
          <p className="text-sm text-stone-500 mt-1">Keep a private inventory of your yarns, hooks, needles, and notions</p>
        </div>

        <button
          onClick={() => {
            setEditingItem({
              category: StashCategory.YARN,
              quantity: 1,
              unitLabel: 'skeins'
            });
            setIsModalOpen(true);
          }}
          className="flex items-center justify-center gap-2 px-5 py-2.5 bg-rose-800 hover:bg-rose-900 text-white rounded-2xl font-medium text-sm shadow-sm transition-all cursor-pointer self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          <span>Add Stash Item</span>
        </button>
      </div>

      {/* Search & Category Bar */}
      <div className="bg-white p-4 rounded-2xl border border-stone-200 shadow-2xs space-y-3 sm:space-y-0 sm:flex sm:items-center sm:gap-4">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search stash by name, brand, or colorway..."
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
          />
        </div>

        <div className="flex items-center gap-2 overflow-x-auto">
          <div className="flex items-center gap-1.5 bg-stone-50 px-3 py-1.5 rounded-xl border border-stone-200 text-xs shrink-0">
            <Filter className="w-3.5 h-3.5 text-stone-500" />
            <select
              value={selectedCategory}
              onChange={e => setSelectedCategory(e.target.value)}
              className="bg-transparent font-medium text-stone-700 focus:outline-none cursor-pointer"
            >
              <option value="ALL">All Categories</option>
              <option value={StashCategory.YARN}>Yarn</option>
              <option value={StashCategory.NEEDLES_HOOKS}>Needles & Hooks</option>
              <option value={StashCategory.NOTIONS}>Notions & Tools</option>
              <option value={StashCategory.MATERIALS}>Other Materials</option>
            </select>
          </div>
        </div>
      </div>

      {/* Stash Grid */}
      {filteredStash.length === 0 ? (
        <div className="bg-white border border-stone-200 rounded-3xl p-12 text-center space-y-3">
          <div className="w-12 h-12 bg-stone-100 text-stone-400 rounded-2xl flex items-center justify-center mx-auto">
            <Package className="w-6 h-6" />
          </div>
          <h3 className="text-base font-semibold text-stone-800">No stash items found</h3>
          <p className="text-xs text-stone-500 max-w-sm mx-auto">
            Click "Add Stash Item" to record yarn skeins, needle sizes, or tools.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredStash.map(item => (
            <div
              key={item.id}
              className="bg-white rounded-3xl p-6 border border-stone-200 shadow-2xs hover:border-stone-300 transition-all flex flex-col justify-between space-y-4"
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-amber-50 text-amber-900 border border-amber-200">
                    {item.category.replace('_', ' ')}
                  </span>
                  <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-stone-100 text-stone-800">
                    {item.quantity} {item.unitLabel}
                  </span>
                </div>

                <div>
                  <h3 className="text-lg font-serif-display font-semibold text-stone-900">{item.name}</h3>
                  {item.brand && (
                    <p className="text-xs text-stone-500 font-medium">{item.brand}</p>
                  )}
                </div>

                <div className="text-xs text-stone-600 space-y-1 bg-stone-50 p-3 rounded-xl border border-stone-100">
                  {item.colorway && <div><strong>Colorway:</strong> {item.colorway} {item.dyeLot && `(Lot: ${item.dyeLot})`}</div>}
                  {item.weightCategory && <div><strong>Weight:</strong> {item.weightCategory}</div>}
                  {item.fiberContent && <div><strong>Fiber:</strong> {item.fiberContent}</div>}
                  {item.yardagePerUnit && <div><strong>Yardage:</strong> {item.yardagePerUnit} yds / unit</div>}
                </div>

                {item.notes && (
                  <p className="text-xs text-stone-600 line-clamp-2 leading-relaxed">
                    {item.notes}
                  </p>
                )}
              </div>

              <div className="pt-3 border-t border-stone-100 flex items-center justify-end gap-2">
                <button
                  onClick={() => {
                    setEditingItem(item);
                    setIsModalOpen(true);
                  }}
                  className="p-1.5 text-stone-500 hover:text-stone-900 hover:bg-stone-100 rounded-lg transition-colors cursor-pointer"
                  title="Edit Item"
                >
                  <Edit3 className="w-4 h-4" />
                </button>
                <button
                  onClick={() => handleDelete(item.id)}
                  className="p-1.5 text-stone-500 hover:text-rose-700 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer"
                  title="Delete Item"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add / Edit Stash Modal */}
      {isModalOpen && editingItem && (
        <div className="fixed inset-0 bg-stone-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSave} className="bg-white rounded-3xl p-6 max-w-lg w-full space-y-4 shadow-xl max-h-[90vh] overflow-y-auto">
            <h3 className="text-lg font-serif-display font-bold text-stone-900">
              {editingItem.id ? 'Edit Stash Item' : 'Add Stash Item'}
            </h3>

            <div>
              <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Item Name *</label>
              <input
                type="text"
                required
                placeholder="e.g. Cascadia Merino Wool, 4.0mm Circulars..."
                value={editingItem.name || ''}
                onChange={e => setEditingItem({ ...editingItem, name: e.target.value })}
                className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Category</label>
                <select
                  value={editingItem.category || StashCategory.YARN}
                  onChange={e => setEditingItem({ ...editingItem, category: e.target.value as StashCategory })}
                  className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800 cursor-pointer"
                >
                  <option value={StashCategory.YARN}>Yarn</option>
                  <option value={StashCategory.NEEDLES_HOOKS}>Needles & Hooks</option>
                  <option value={StashCategory.NOTIONS}>Notions & Tools</option>
                  <option value={StashCategory.MATERIALS}>Other Materials</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Brand / Maker</label>
                <input
                  type="text"
                  placeholder="e.g. Cascade Yarns..."
                  value={editingItem.brand || ''}
                  onChange={e => setEditingItem({ ...editingItem, brand: e.target.value })}
                  className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Quantity</label>
                <input
                  type="number"
                  value={editingItem.quantity ?? 1}
                  onChange={e => setEditingItem({ ...editingItem, quantity: parseInt(e.target.value) || 0 })}
                  className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Unit Label</label>
                <input
                  type="text"
                  placeholder="e.g. skeins, pairs, pcs"
                  value={editingItem.unitLabel || 'skeins'}
                  onChange={e => setEditingItem({ ...editingItem, unitLabel: e.target.value })}
                  className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
                />
              </div>
            </div>

            {editingItem.category === StashCategory.YARN && (
              <>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Colorway</label>
                    <input
                      type="text"
                      placeholder="e.g. Forest Moss"
                      value={editingItem.colorway || ''}
                      onChange={e => setEditingItem({ ...editingItem, colorway: e.target.value })}
                      className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Dye Lot</label>
                    <input
                      type="text"
                      placeholder="e.g. 4021"
                      value={editingItem.dyeLot || ''}
                      onChange={e => setEditingItem({ ...editingItem, dyeLot: e.target.value })}
                      className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Weight Category</label>
                    <input
                      type="text"
                      placeholder="e.g. Worsted, Fingering"
                      value={editingItem.weightCategory || ''}
                      onChange={e => setEditingItem({ ...editingItem, weightCategory: e.target.value })}
                      className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Yardage / Unit</label>
                    <input
                      type="number"
                      placeholder="e.g. 220"
                      value={editingItem.yardagePerUnit || ''}
                      onChange={e => setEditingItem({ ...editingItem, yardagePerUnit: parseInt(e.target.value) || undefined })}
                      className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Fiber Content</label>
                  <input
                    type="text"
                    placeholder="e.g. 100% Merino Wool"
                    value={editingItem.fiberContent || ''}
                    onChange={e => setEditingItem({ ...editingItem, fiberContent: e.target.value })}
                    className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
                  />
                </div>
              </>
            )}

            <div>
              <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Notes</label>
              <textarea
                rows={2}
                placeholder="Storage location, intended project..."
                value={editingItem.notes || ''}
                onChange={e => setEditingItem({ ...editingItem, notes: e.target.value })}
                className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
              />
            </div>

            <div className="pt-3 flex items-center justify-end gap-2 border-t border-stone-100">
              <button
                type="button"
                onClick={() => {
                  setIsModalOpen(false);
                  setEditingItem(null);
                }}
                className="px-4 py-2 text-xs font-medium text-stone-600 hover:bg-stone-100 rounded-xl cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-5 py-2 bg-rose-800 hover:bg-rose-900 text-white font-medium rounded-xl text-xs cursor-pointer"
              >
                Save Stash Item
              </button>
            </div>
          </form>
        </div>
      )}

    </div>
  );
};
