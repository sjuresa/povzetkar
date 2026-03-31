import { useTranslation } from 'react-i18next';
import heroAsset from '../../assets/hero-distillation.png';

export function VisionSection() {
  const { t } = useTranslation();

  return (
    <section id="vision" className="max-w-[1400px] mx-auto">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-24 items-center">
         <div className="space-y-16">
            <div className="space-y-8">
               <h2 className="text-5xl md:text-7xl font-black uppercase tracking-tighter leading-[0.9] italic">
                  {t('landing.vision.title')}
               </h2>
               <div className="space-y-6 max-w-xl">
                  <p className="text-2xl font-black uppercase tracking-tight text-[var(--color-accent)]">
                    {t('landing.vision.desc1')}
                  </p>
                  <p className="text-xl font-medium leading-relaxed text-[var(--color-text-secondary)]">
                    {t('landing.vision.desc2')}
                  </p>
               </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-12 border-t-4 border-[var(--color-border-subtle)] pt-12">
               {[
                 { title: t('landing.vision.pillar1_title'), desc: t('landing.vision.pillar1_desc') },
                 { title: t('landing.vision.pillar2_title'), desc: t('landing.vision.pillar2_desc') },
                 { title: t('landing.vision.pillar3_title'), desc: t('landing.vision.pillar3_desc') },
                 { title: t('landing.vision.pillar4_title'), desc: t('landing.vision.pillar4_desc') }
               ].map((pillar, idx) => (
                 <div key={idx} className="space-y-3">
                    <h4 className="text-lg font-black uppercase tracking-widest italic flex items-center gap-2">
                       <div className="w-2 h-6 bg-[var(--color-accent)]"></div>
                       {pillar.title}
                    </h4>
                    <p className="text-sm font-bold text-[var(--color-text-secondary)] leading-relaxed">{pillar.desc}</p>
                 </div>
               ))}
            </div>
         </div>

         <div className="relative group perspective-[2000px]">
            <div className="absolute inset-0 bg-[var(--color-accent)] translate-x-8 translate-y-8 group-hover:translate-x-4 group-hover:translate-y-4 transition-transform duration-700"></div>
            <div className="relative border-4 border-black bg-[var(--color-surface-deep)] overflow-hidden">
               <img 
                 src={heroAsset} 
                 alt="Wisdom Inception" 
                 className="w-full h-full object-cover grayscale brightness-110 group-hover:grayscale-0 transition-all duration-1000 scale-105 group-hover:scale-100" 
               />
               <div className="absolute inset-x-0 bottom-0 p-8 bg-gradient-to-t from-black/80 to-transparent flex flex-col justify-end h-1/2">
                  <span className="text-[10px] font-black uppercase tracking-[0.4em] text-white/60 mb-2 italic">{t('landing.vision.image_label')}</span>
                  <h5 className="text-2xl font-black text-white uppercase tracking-tighter italic">{t('landing.vision.image_text')}</h5>
               </div>
            </div>
         </div>
      </div>
    </section>
  );
}
