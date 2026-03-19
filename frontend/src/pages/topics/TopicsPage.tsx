import React, { useState } from 'react';
import { TopicList } from './TopicList';
import { TopicForm } from './TopicForm';
import type { Topic } from '../../api/generated/model';
import { Plus, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export const TopicsPage: React.FC = () => {
  const { t } = useTranslation();
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingTopic, setEditingTopic] = useState<Topic | undefined>(undefined);

  const handleEdit = (topic: Topic) => {
    setEditingTopic(topic);
    setIsFormOpen(true);
  };

  const closeForm = () => {
    setIsFormOpen(false);
    setEditingTopic(undefined);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-10 space-y-10">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div>
          <h1 className="text-4xl font-extrabold text-[var(--color-text-primary)] tracking-tight">
            {t('topics.title')}
          </h1>
          <p className="mt-2 text-[var(--color-text-secondary)] text-lg font-medium">
            {t('topics.subtitle')}
          </p>
        </div>

        <button
          onClick={() => {
            setEditingTopic(undefined);
            setIsFormOpen(true);
          }}
          className="inline-flex items-center px-6 py-3 bg-blue-600 text-white rounded-2xl font-bold shadow-lg shadow-blue-500/25 hover:bg-blue-700 hover:scale-[1.02] active:scale-95 transition-all"
        >
          <Plus className="w-5 h-5 mr-2" />
          {t('topics.add_topic')}
        </button>
      </div>

      <div className="relative">
        <div className="absolute -inset-4 bg-gradient-to-r from-blue-500/5 to-purple-500/5 blur-3xl rounded-[40px] pointer-events-none -z-10" />
        <TopicList onEdit={handleEdit} />
      </div>

      {isFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div 
            className="absolute inset-0 bg-slate-900/40 backdrop-blur-md transition-opacity"
            onClick={closeForm}
          />
          <div className="relative w-full max-w-2xl bg-[var(--color-surface-elevated)] rounded-[40px] shadow-2xl border border-[var(--color-border-subtle)] overflow-hidden flex flex-col">
            <div className="p-8 border-b border-[var(--color-border-subtle)] flex items-center justify-between">
              <h2 className="text-2xl font-bold text-[var(--color-text-primary)]">
                {editingTopic ? t('topics.edit_topic') : t('topics.add_topic')}
              </h2>
              <button 
                onClick={closeForm}
                className="p-2 hover:bg-[var(--color-background-alt)] rounded-xl transition-colors text-[var(--color-text-secondary)]"
              >
                <X className="w-6 h-6" />
              </button>
            </div>
            
            <div className="p-8 max-h-[80vh] overflow-y-auto custom-scrollbar">
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
