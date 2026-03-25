import React, { useState } from 'react';
import { TopicList } from './TopicList';
import { TopicForm } from './TopicForm';
import type { Topic } from '../../api/generated/model';
import { Plus, X, Search, CheckCircle2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export const TopicsPage: React.FC = () => {
  const { t } = useTranslation();
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingTopic, setEditingTopic] = useState<Topic | undefined>(undefined);
  const [searchQuery, setSearchQuery] = useState('');
  const [showActiveOnly, setShowActiveOnly] = useState(false);

  const handleEdit = (topic: Topic) => {
    setEditingTopic(topic);
    setIsFormOpen(true);
  };

  const closeForm = () => {
    setIsFormOpen(false);
    setEditingTopic(undefined);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-12">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-8">
        <div>
          <h1 className="text-5xl font-extrabold text-[var(--color-text-primary)] tracking-tight">
            {t('topics.title')}
          </h1>
          <p className="mt-3 text-[var(--color-text-secondary)] text-xl font-medium">
            {t('topics.subtitle')}
          </p>
        </div>

        <button
          onClick={() => {
            setEditingTopic(undefined);
            setIsFormOpen(true);
          }}
          className="inline-flex items-center px-8 py-4 bg-blue-600 text-white rounded-2xl font-bold shadow-xl shadow-blue-500/30 hover:bg-blue-700 hover:scale-[1.03] active:scale-95 transition-all text-lg"
        >
          <Plus className="w-6 h-6 mr-3" />
          {t('topics.add_topic')}
        </button>
      </div>

      {/* Modern Filter bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 bg-[var(--color-background-alt)] p-4 rounded-3xl border border-[var(--color-border-subtle)]">
        <div className="relative flex-1 group">
          <Search className="absolute left-5 top-1/2 -translate-y-1/2 w-5 h-5 text-[var(--color-text-secondary)] group-focus-within:text-blue-600 transition-colors" />
          <input
            type="text"
            placeholder={t('topics.list.search_placeholder')}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-14 pr-4 py-4 rounded-2xl border border-[var(--color-border-subtle)] bg-[var(--color-background-base)] text-[var(--color-text-primary)] focus:ring-2 focus:ring-blue-600 focus:border-transparent transition-all"
          />
        </div>
        
        {/* Toggle Filter for Active Only */}
        <button
          onClick={() => setShowActiveOnly(!showActiveOnly)}
          className={`flex items-center px-6 py-4 rounded-2xl border transition-all font-bold ${
            showActiveOnly 
              ? 'bg-blue-600 text-white border-blue-600 shadow-lg shadow-blue-500/20' 
              : 'bg-[var(--color-background-base)] text-[var(--color-text-secondary)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)]'
          }`}
        >
          <CheckCircle2 className={`w-5 h-5 mr-2.5 ${showActiveOnly ? 'text-white' : 'text-green-500'}`} />
          {t('topics.list.show_active_only')}
        </button>
      </div>

      <div className="relative">
        <div className="absolute -inset-8 bg-gradient-to-r from-blue-500/5 to-purple-500/5 blur-3xl rounded-[40px] pointer-events-none -z-10" />
        <TopicList 
          onEdit={handleEdit} 
          searchQuery={searchQuery} 
          showActiveOnly={showActiveOnly}
        />
      </div>

      {isFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div 
            className="absolute inset-0 bg-slate-900/40 backdrop-blur-md transition-opacity"
            onClick={closeForm}
          />
          <div className="relative w-full max-w-2xl bg-[var(--color-surface-elevated)] rounded-[40px] shadow-2xl border border-[var(--color-border-subtle)] overflow-hidden flex flex-col scale-in">
            <div className="p-8 border-b border-[var(--color-border-subtle)] flex items-center justify-between bg-[var(--color-background-alt)]">
              <h2 className="text-3xl font-bold text-[var(--color-text-primary)]">
                {editingTopic ? t('topics.edit_topic') : t('topics.add_topic')}
              </h2>
              <button 
                onClick={closeForm}
                className="p-3 hover:bg-[var(--color-background-base)] rounded-2xl transition-all text-[var(--color-text-secondary)] hover:scale-110"
              >
                <X className="w-8 h-8" />
              </button>
            </div>
            
            <div className="p-10 max-h-[80vh] overflow-y-auto custom-scrollbar bg-[var(--color-background-base)]">
              <TopicForm 
                initialData={editingTopic}
                onSuccess={() => {
                  closeForm();
                }}
                onCancel={closeForm}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
