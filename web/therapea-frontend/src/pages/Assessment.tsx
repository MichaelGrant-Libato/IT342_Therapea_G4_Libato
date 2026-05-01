import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import '../styles/Assessment.css';

interface Question { id: number; section: string; text: string; sensitive?: boolean; }

const QUESTIONS: Question[] = [
  { id:1,  section:'Mood',    text:'Over the last 2 weeks, how often have you had little interest or pleasure in doing things?' },
  { id:2,  section:'Mood',    text:'Over the last 2 weeks, how often have you felt down, depressed, or hopeless?' },
  { id:3,  section:'Mood',    text:'Over the last 2 weeks, how often have you had trouble falling or staying asleep, or sleeping too much?' },
  { id:4,  section:'Mood',    text:'Over the last 2 weeks, how often have you felt tired or had little energy?' },
  { id:5,  section:'Mood',    text:'Over the last 2 weeks, how often have you had poor appetite or been overeating?' },
  { id:6,  section:'Mood',    text:'Over the last 2 weeks, how often have you felt bad about yourself — like you let yourself or your family down?' },
  { id:7,  section:'Mood',    text:'Over the last 2 weeks, how often have you had trouble concentrating on things like reading or watching TV?' },
  { id:8,  section:'Mood',    text:'Over the last 2 weeks, how often have you moved or spoken so slowly others might have noticed — or been unusually fidgety?' },
  { id:9,  section:'Mood',    text:'Over the last 2 weeks, have you had thoughts of hurting yourself or that you would be better off dead?', sensitive:true },
  { id:10, section:'Anxiety', text:'Over the last 2 weeks, how often have you felt nervous, anxious, or on edge?' },
  { id:11, section:'Anxiety', text:'Over the last 2 weeks, how often have you been unable to stop or control worrying?' },
  { id:12, section:'Anxiety', text:'Over the last 2 weeks, how often have you worried too much about different things?' },
  { id:13, section:'Anxiety', text:'Over the last 2 weeks, how often have you had trouble relaxing?' },
  { id:14, section:'Anxiety', text:'Over the last 2 weeks, how often have you been so restless it was hard to sit still?' },
  { id:15, section:'Anxiety', text:'Over the last 2 weeks, how often have you become easily annoyed or irritable?' },
  { id:16, section:'Anxiety', text:'Over the last 2 weeks, how often have you felt afraid something awful might happen?' },
];

const OPTIONS = [
  { label:'Not at all',              value:0 },
  { label:'Several days',            value:1 },
  { label:'More than half the days', value:2 },
  { label:'Nearly every day',        value:3 },
];

const calcRisk = (answers: number[]) => {
  const phq9  = answers.slice(0, 9).reduce((s,v) => s + Math.max(0,v), 0);
  const gad7  = answers.slice(9).reduce((s,v) => s + Math.max(0,v), 0);
  const total = phq9 + gad7;
  const score = Math.round((total / 48) * 100);
  let level = 'Low';
  if (score >= 75)      level = 'High';
  else if (score >= 50) level = 'Moderate';
  else if (score >= 25) level = 'Mild';
  return { phq9, gad7, total, score, level };
};

const RECOMMENDATIONS: Record<string, string> = {
  Low:      'Your responses show minimal symptoms right now. Keep up the habits that support your wellbeing, and consider a check-in with a mental health professional every few months.',
  Mild:     'Your responses suggest some mild symptoms. Talking with a therapist can give you useful tools for what you\'re experiencing — it\'s a good time to reach out.',
  Moderate: 'Your responses point to a moderate level of distress. Support from a licensed therapist or counselor is recommended. Effective, evidence-based treatment options are available.',
  High:     'Your responses indicate a high level of distress. Please reach out to a mental health professional soon. A licensed clinician can create a personalised plan that addresses what you\'re going through.',
};

const PHQ9_LABELS: Record<number, string> = { 0:'None-minimal', 1:'Mild', 2:'Moderate', 3:'Mod. severe', 4:'Severe' };
const GAD7_LABELS: Record<number, string> = { 0:'Minimal', 1:'Mild', 2:'Moderate', 3:'Severe' };
const phq9Label  = (s: number) => s <= 4 ? PHQ9_LABELS[0] : s <= 9 ? PHQ9_LABELS[1] : s <= 14 ? PHQ9_LABELS[2] : s <= 19 ? PHQ9_LABELS[3] : PHQ9_LABELS[4];
const gad7Label  = (s: number) => s <= 4 ? GAD7_LABELS[0] : s <= 9 ? GAD7_LABELS[1] : s <= 14 ? GAD7_LABELS[2] : GAD7_LABELS[3];

type Screen = 'intro' | 'quiz' | 'results';

const Assessment: React.FC = () => {
  const navigate = useNavigate();
  const [screen,      setScreen]      = useState<Screen>('intro');
  const [currentQ,    setCurrentQ]    = useState(0);
  const [answers,     setAnswers]     = useState<number[]>(new Array(QUESTIONS.length).fill(-1));
  const [selected,    setSelected]    = useState<number | null>(null);
  const [isSaving,    setIsSaving]    = useState(false);
  const [saveError,   setSaveError]   = useState('');
  const [results,     setResults]     = useState<ReturnType<typeof calcRisk> | null>(null);
  const [errorMsg,    setErrorMsg]    = useState('');

  const total   = QUESTIONS.length;
  const pct     = Math.round((currentQ / total) * 100);
  const isLast  = currentQ === total - 1;
  const q       = QUESTIONS[currentQ];

  const handleSelect = (val: number) => { setSelected(val); setErrorMsg(''); };

  const handleNext = () => {
    if (selected === null) { setErrorMsg('Please choose an answer before continuing.'); return; }
    const updated = [...answers];
    updated[currentQ] = selected;
    setAnswers(updated);

    if (!isLast) {
      setCurrentQ(c => c + 1);
      setSelected(updated[currentQ + 1] >= 0 ? updated[currentQ + 1] : null);
    } else {
      finalize(updated);
    }
  };

  const handleBack = () => {
    if (currentQ === 0) { setScreen('intro'); return; }
    const updated = [...answers];
    if (selected !== null) updated[currentQ] = selected;
    setAnswers(updated);
    const prev = currentQ - 1;
    setCurrentQ(prev);
    setSelected(updated[prev] >= 0 ? updated[prev] : null);
  };

  const finalize = async (finalAnswers: number[]) => {
    const r = calcRisk(finalAnswers);
    setResults(r);
    setScreen('results');

    const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
    if (!stored) return;
    const user = JSON.parse(stored);

    const newAssessment = {
      id: `asm_${Date.now()}`,
      assessmentType: 'Triage Assessment',
      phq9Score: r.phq9,
      gad7Score: r.gad7,
      clinicalScore: r.score,
      riskLevel: r.level,
      status: 'Pending',
      createdAt: new Date().toISOString()
    };
    const existingHistory = JSON.parse(localStorage.getItem(`assessments_${user.email}`) || '[]');
    localStorage.setItem(`assessments_${user.email}`, JSON.stringify([newAssessment, ...existingHistory]));

    try {
      setIsSaving(true);
      const res = await fetch('http://localhost:8083/api/assessments/save', {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: user.email, 
          userId: user.userId,
          patientName: user.fullName || user.name || 'Patient', // <--- Added patientName here
          assessmentType: 'Triage Assessment',
          phq9Score: r.phq9, 
          gad7Score: r.gad7,
          totalScore: r.total, 
          clinicalScore: r.score,
          riskLevel: r.level,
          status: 'Pending',
          answers: JSON.stringify(finalAnswers)
        }),
      });
      
      const data = await res.json();
      if (!data.success) throw new Error("API Save Failed");

    } catch (err: any) {
      console.error("Backend unreachable, saved to local storage instead.", err);
      setSaveError("Saved locally. Backend sync failed.");
    } finally {
      setIsSaving(false);
    }
  };

  if (screen === 'intro') return (
    <div className="asm-container">
      <div className="asm-card intro-card">
        <div className="asm-graphic">
          <div className="asm-icon-wrap">
            <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M9.5 2A2.5 2.5 0 0 1 12 4.5v15a2.5 2.5 0 0 1-5 0v-15A2.5 2.5 0 0 1 9.5 2z"/>
              <path d="M14.5 2A2.5 2.5 0 0 0 12 4.5v15a2.5 2.5 0 0 0 5 0v-15A2.5 2.5 0 0 0 14.5 2z"/>
              <path d="M12 4.5a2.5 2.5 0 1 0-5 0"/><path d="M12 4.5a2.5 2.5 0 1 1 5 0"/>
            </svg>
          </div>
          <div className="asm-graphic-line" />
        </div>
        <h1 className="asm-title">Smart Triage Assessment</h1>
        <div className="asm-info-box">
          This assessment uses clinically validated questions (PHQ-9 and GAD-7) to get a picture
          of how you have been feeling over the past two weeks. It takes about 5–10 minutes.
          Your answers are private and used only to give you a personalised result.
        </div>
        <button className="asm-btn-primary" onClick={() => setScreen('quiz')}>Start the assessment</button>
        <button className="asm-btn-text" onClick={() => navigate('/dashboard')} style={{ marginTop: 16 }}>← Back</button>
      </div>
    </div>
  );

  if (screen === 'quiz') return (
    <div className="asm-container">
      <div className="asm-card quiz-card">
        <div className="asm-progress-wrap"><div className="asm-progress-fill" style={{ width:`${pct}%` }} /></div>
        <div className="asm-meta"><span className="asm-pct">{pct}% complete</span><span className="asm-count">Question {currentQ + 1} of {total}</span></div>
        <div className="asm-section-label">{q.section}</div>
        <h2 className="asm-question">{q.text}</h2>
        {q.sensitive && (
          <div className="asm-alert">If you are in crisis right now, please call or text <strong>988</strong> (Suicide and Crisis Lifeline) for immediate support.</div>
        )}
        <div className="asm-options">
          {OPTIONS.map(opt => (
            <button key={opt.value} className={`asm-option-btn ${selected === opt.value ? 'selected' : ''}`} onClick={() => handleSelect(opt.value)}>
              <div className="asm-radio">{selected === opt.value && <div className="asm-radio-dot" />}</div>
              {opt.label}
            </button>
          ))}
        </div>
        {errorMsg && <div className="asm-error">{errorMsg}</div>}
        <div className="asm-footer">
          <button className="asm-btn-outline" onClick={handleBack}>Back</button>
          <button className="asm-btn-primary" disabled={selected === null || isSaving} onClick={handleNext}>{isSaving ? 'Saving…' : isLast ? 'See my results' : 'Next'}</button>
        </div>
      </div>
    </div>
  );

  if (screen === 'results' && results) {
    const dotClass = results.level.toLowerCase();
    return (
      <div className="asm-container">
        <div className="asm-card results-card">
          <div className="asm-results-header"><h2 className="asm-title">Your results</h2></div>
          <div className="asm-risk-box">
            <div className="asm-risk-label">Calculated risk level</div>
            <div className="asm-risk-val-row">
              <span className="asm-risk-val">{results.level}</span>
              <div className={`asm-risk-dot ${dotClass}`} />
            </div>
          </div>
          <div className="asm-score-wrap">
            <div className="asm-score-label">Clinical score</div>
            <div className="asm-score-num">{results.score}</div>
            <div className="asm-score-max">out of 100</div>
          </div>
          <div className="asm-subscores">
            <div className="asm-sub-card">
              <div className="asm-sub-label">PHQ-9 Depression</div>
              <div className="asm-sub-val">{results.phq9}</div>
              <div className="asm-sub-desc">/ 27 — {phq9Label(results.phq9)}</div>
            </div>
            <div className="asm-sub-card">
              <div className="asm-sub-label">GAD-7 Anxiety</div>
              <div className="asm-sub-val">{results.gad7}</div>
              <div className="asm-sub-desc">/ 21 — {gad7Label(results.gad7)}</div>
            </div>
          </div>
          <div className="asm-rec-box">
            <div className="asm-rec-title">What this means for you</div>
            <p>{RECOMMENDATIONS[results.level]}</p>
          </div>
          
          {isSaving && <div className="asm-saving" style={{ textAlign: 'center', color: 'var(--primary)', marginBottom: '16px' }}>Saving to database...</div>}
          {saveError && <div className="asm-error">{saveError}</div>}
          
          <div className="asm-actions">
            <button className="asm-btn-primary" onClick={() => navigate('/therapists')}>Find a therapist</button>
            <button className="asm-btn-outline" onClick={() => navigate('/emergency')}>Emergency Map</button>
          </div>
          <button className="asm-btn-text" onClick={() => navigate('/dashboard')}>← Back to dashboard</button>
        </div>
      </div>
    );
  }
  return null;
};

export default Assessment;