import React, { useState } from 'react';
import { LibraryItem, Craft, CraftLabels } from '../../types';
import { getLibraryItems, saveLibraryItem, deleteLibraryItem } from '../../services/db';
import { Plus, Search, BookOpen, Bookmark, BookmarkCheck, ExternalLink, Trash2, Edit3, Tag } from 'lucide-react';

export const LibraryScreen: React.FC = () => {
  const [items, setItems] = useState<LibraryItem[]>(() => getLibraryItems());
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCraft, setSelectedCraft] = useState<string>('ALL');
  const [onlyBookmarked, setOnlyBookmarked] = useState<boolean>(false);

  const [editingItem, setEditingItem] = useState<Partial<LibraryItem> | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const refreshItems = () => {
    setItems(getLibraryItems());
  };

  const handleToggleBookmark = (item: LibraryItem) => {
    saveLibraryItem({
      ...item,
      bookmarked: !item.bookmarked
    });
    refreshItems();
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingItem || !editingItem.title) return;

    const tagsArray = typeof editingItem.tags === 'string'
      ? (editingItem.tags as string).split(',').map(t => t.trim()).filter(Boolean)
      : (editingItem.tags || []);

    saveLibraryItem({
      id: editingItem.id,
      title: editingItem.title,
      craft: editingItem.craft || Craft.KNITTING,
      author: editingItem.author,
      sourceUrl: editingItem.sourceUrl,
      tags: tagsArray,
      notes: editingItem.notes,
      bookmarked: editingItem.bookmarked ?? false
    });

    setIsModalOpen(false);
    setEditingItem(null);
    refreshItems();
  };

  const handleDelete = (id: string) => {
    deleteLibraryItem(id);
    refreshItems();
  };

  const filteredItems = items.filter(item => {
    const matchesSearch = item.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (item.author && item.author.toLowerCase().includes(searchTerm.toLowerCase())) ||
      item.tags.some(t => t.toLowerCase().includes(searchTerm.toLowerCase()));

    const matchesCraft = selectedCraft === 'ALL' || item.craft === selectedCraft;
    const matchesBookmark = !onlyBookmarked || item.bookmarked;

    return matchesSearch && matchesCraft && matchesBookmark;
  });

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-8 py-8 space-y-8 animate-fade-in">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-serif-display font-bold text-stone-900">Pattern Library</h1>
          <p className="text-sm text-stone-500 mt-1">Store pattern references, technique notes, and bookmarked craft guides</p>
        </div>

        <button
          onClick={() => {
            setEditingItem({
              craft: Craft.KNITTING,
              bookmarked: false,
              tags: []
            });
            setIsModalOpen(true);
          }}
          className="flex items-center justify-center gap-2 px-5 py-2.5 bg-rose-800 hover:bg-rose-900 text-white rounded-2xl font-medium text-sm shadow-sm transition-all cursor-pointer self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" />
          <span>Add Library Pattern</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-stone-200 shadow-2xs space-y-3 sm:space-y-0 sm:flex sm:items-center sm:gap-4">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-stone-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search pattern library by title, author, or tags..."
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
          />
        </div>

        <div className="flex items-center gap-2 overflow-x-auto">
          <div className="flex items-center gap-1.5 bg-stone-50 px-3 py-1.5 rounded-xl border border-stone-200 text-xs shrink-0">
            <select
              value={selectedCraft}
              onChange={e => setSelectedCraft(e.target.value)}
              className="bg-transparent font-medium text-stone-700 focus:outline-none cursor-pointer"
            >
              <option value="ALL">All Crafts</option>
              {Object.entries(CraftLabels).map(([key, label]) => (
                <option key={key} value={key}>{label}</option>
              ))}
            </select>
          </div>

          <button
            onClick={() => setOnlyBookmarked(!onlyBookmarked)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl border text-xs font-medium transition-colors shrink-0 cursor-pointer ${
              onlyBookmarked
                ? 'bg-amber-100 text-amber-900 border-amber-300'
                : 'bg-stone-50 text-stone-600 border-stone-200 hover:bg-stone-100'
            }`}
          >
            <Bookmark className="w-3.5 h-3.5" /> Bookmarks Only
          </button>
        </div>
      </div>

      {/* Grid */}
      {filteredItems.length === 0 ? (
        <div className="bg-white border border-stone-200 rounded-3xl p-12 text-center space-y-3">
          <div className="w-12 h-12 bg-stone-100 text-stone-400 rounded-2xl flex items-center justify-center mx-auto">
            <BookOpen className="w-6 h-6" />
          </div>
          <h3 className="text-base font-semibold text-stone-800">No pattern references found</h3>
          <p className="text-xs text-stone-500 max-w-sm mx-auto">
            Click "Add Library Pattern" to save technique references or bookmarked guides.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredItems.map(item => (
            <div
              key={item.id}
              className="bg-white rounded-3xl p-6 border border-stone-200 shadow-2xs hover:border-stone-300 transition-all flex flex-col justify-between space-y-4"
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-900 border border-emerald-200">
                    {CraftLabels[item.craft]}
                  </span>

                  <button
                    onClick={() => handleToggleBookmark(item)}
                    className="p-1.5 text-stone-400 hover:text-amber-600 rounded-lg transition-colors cursor-pointer"
                  >
                    {item.bookmarked ? (
                      <BookmarkCheck className="w-5 h-5 text-amber-600 fill-amber-100" />
                    ) : (
                      <Bookmark className="w-5 h-5" />
                    )}
                  </button>
                </div>

                <div>
                  <h3 className="text-lg font-serif-display font-semibold text-stone-900">{item.title}</h3>
                  {item.author && (
                    <p className="text-xs text-stone-500 font-medium">By {item.author}</p>
                  )}
                </div>

                {item.tags.length > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {item.tags.map((tag, idx) => (
                      <span key={idx} className="inline-flex items-center gap-1 text-[11px] px-2 py-0.5 bg-stone-100 text-stone-600 rounded-md">
                        <Tag className="w-2.5 h-2.5 text-stone-400" /> {tag}
                      </span>
                    ))}
                  </div>
                )}

                {item.notes && (
                  <p className="text-xs text-stone-600 line-clamp-3 leading-relaxed bg-stone-50 p-3 rounded-xl border border-stone-100">
                    {item.notes}
                  </p>
                )}
              </div>

              <div className="pt-3 border-t border-stone-100 flex items-center justify-between text-xs text-stone-500">
                {item.sourceUrl ? (
                  <a
                    href={item.sourceUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-xs font-semibold text-rose-800 hover:underline inline-flex items-center gap-1"
                  >
                    <span>Source</span> <ExternalLink className="w-3 h-3" />
                  </a>
                ) : <span />}

                <div className="flex items-center gap-1">
                  <button
                    onClick={() => {
                      setEditingItem({
                        ...item,
                        tags: item.tags.join(', ') as any
                      });
                      setIsModalOpen(true);
                    }}
                    className="p-1.5 text-stone-500 hover:text-stone-900 hover:bg-stone-100 rounded-lg transition-colors cursor-pointer"
                    title="Edit Pattern Reference"
                  >
                    <Edit3 className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDelete(item.id)}
                    className="p-1.5 text-stone-500 hover:text-rose-700 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer"
                    title="Delete Pattern Reference"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add / Edit Modal */}
      {isModalOpen && editingItem && (
        <div className="fixed inset-0 bg-stone-900/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <form onSubmit={handleSave} className="bg-white rounded-3xl p-6 max-w-lg w-full space-y-4 shadow-xl">
            <h3 className="text-lg font-serif-display font-bold text-stone-900">
              {editingItem.id ? 'Edit Pattern Reference' : 'Add Pattern Reference'}
            </h3>

            <div>
              <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Pattern Title *</label>
              <input
                type="text"
                required
                placeholder="e.g. Classic Raglan Construction Guide..."
                value={editingItem.title || ''}
                onChange={e => setEditingItem({ ...editingItem, title: e.target.value })}
                className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Craft</label>
                <select
                  value={editingItem.craft || Craft.KNITTING}
                  onChange={e => setEditingItem({ ...editingItem, craft: e.target.value as Craft })}
                  className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800 cursor-pointer"
                >
                  {Object.entries(CraftLabels).map(([key, label]) => (
                    <option key={key} value={key}>{label}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Author / Designer</label>
                <input
                  type="text"
                  placeholder="e.g. Designer Name"
                  value={editingItem.author || ''}
                  onChange={e => setEditingItem({ ...editingItem, author: e.target.value })}
                  className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Source Link (URL)</label>
              <input
                type="url"
                placeholder="https://..."
                value={editingItem.sourceUrl || ''}
                onChange={e => setEditingItem({ ...editingItem, sourceUrl: e.target.value })}
                className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Tags (Comma-separated)</label>
              <input
                type="text"
                placeholder="e.g. sweater, raglan, technique"
                value={editingItem.tags as any || ''}
                onChange={e => setEditingItem({ ...editingItem, tags: e.target.value as any })}
                className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-800"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">Notes & Key References</label>
              <textarea
                rows={3}
                placeholder="Calculations, size charts, stitch details..."
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
                Save Pattern
              </button>
            </div>
          </form>
        </div>
      )}

    </div>
  );
};
