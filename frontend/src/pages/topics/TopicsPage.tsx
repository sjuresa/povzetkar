import React, { useState } from 'react';
import { TopicList } from './TopicList';
import { TopicForm } from './TopicForm';
import type { Topic } from '../../api/generated/model';
import { Plus, X, Search, CheckCircle2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../../store/authStore';

export const TopicsPage: React.FC = () => {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const readonly = !(user?.permissions?.includes('TOPIC_CREATE') ?? false);
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

        {!readonly && (
          <button
            onClick={() => {
              setEditingTopic(undefined);
              setIsFormOpen(true);
            }}
            className="inline-flex items-center px-8 py-4 bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] rounded-xl md:rounded-[28px] font-bold shadow-xl shadow-[var(--color-action-primary)]/20 hover:bg-[var(--color-action-hover)] hover:scale-[1.03] active:scale-95 transition-all text-lg"
          >
            <Plus className="w-6 h-6 mr-3"  strokeWidth={1.5}/>
            {t('topics.add_topic')}
          </button>
        )}
      </div>

      {/* Modern Filter bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 bg-[var(--color-background-alt)] p-4 rounded-xl md:rounded-[28px] border border-[var(--color-border-subtle)]">
        <div className="relative flex-1 group">
          <Search className="absolute left-5 top-1/2 -translate-y-1/2 w-5 h-5 text-[var(--color-text-secondary)] group-focus-within:text-[var(--color-action-primary)] transition-colors"  strokeWidth={1.5}/>
          <input
            type="text"
            placeholder={t('topics.list.search_placeholder')}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-14 pr-4 py-4 rounded-xl md:rounded-[28px] border border-[var(--color-border-subtle)] bg-[var(--color-background-base)] text-[var(--color-text-primary)] focus:ring-2 focus:ring-[var(--color-ring-focus)]/20 focus:border-[var(--color-action-primary)] transition-all outline-none"
          />
        </div>
        
        {/* Toggle Filter for Active Only */}
        <button
          onClick={() => setShowActiveOnly(!showActiveOnly)}
          className={`flex items-center px-6 py-4 rounded-xl md:rounded-[28px] border transition-all font-bold ${
            showActiveOnly 
              ? 'bg-[var(--color-action-primary)] text-[var(--color-action-primary-text)] border-[var(--color-action-primary)]/50 shadow-lg shadow-[var(--color-action-primary)]/20' 
              : 'bg-[var(--color-background-base)] text-[var(--color-text-secondary)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)]'
          }`}
        >
          <CheckCircle2 className={`w-5 h-5 mr-2.5 ${showActiveOnly ? 'text-[var(--color-action-primary-text)]' : 'text-[var(--color-text-secondary)]/50'}`}  strokeWidth={1.5}/>
          {t('topics.list.show_active_only')}
        </button>
      </div>

      <div className="relative">
        <div className="absolute -inset-8 bg-gradient-to-r from-[var(--color-action-primary)]/5 to-transparent blur-3xl rounded-[40px] pointer-events-none -z-10" />
        <TopicList
          onEdit={handleEdit}
          searchQuery={searchQuery}
          showActiveOnly={showActiveOnly}
          readonly={readonly}
        />
      </div>

      {isFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div 
            className="absolute inset-0 bg-[var(--color-bg-primary)]/40 backdrop-blur-md transition-opacity"
            onClick={closeForm}
          />
          <div className="relative w-full max-w-2xl bg-[var(--color-surface-elevated)] rounded-[40px] shadow-2xl border border-[var(--color-border-subtle)] overflow-hidden flex flex-col scale-in">
            <div className="p-8 border-b border-[var(--color-border-subtle)] flex items-center justify-between bg-[var(--color-background-alt)]">
              <h2 className="text-3xl font-bold text-[var(--color-text-primary)]">
                {editingTopic ? t('topics.edit_topic') : t('topics.add_topic')}
              </h2>
              <button 
                onClick={closeForm}
                className="p-3 hover:bg-[var(--color-background-base)] rounded-xl md:rounded-[28px] transition-all text-[var(--color-text-secondary)] hover:scale-110"
              >
                <X className="w-8 h-8"  strokeWidth={1.5}/>
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
