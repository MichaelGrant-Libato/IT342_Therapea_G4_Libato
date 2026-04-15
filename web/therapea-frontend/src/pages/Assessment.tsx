import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import '../styles/Assessment.css';

const QUESTIONS = [
  "Over the last 2 weeks, how often have you felt down, depressed, or hopeless?",
  "How often have you had little interest or pleasure in doing things?",
  "Trouble falling or staying asleep, or sleeping too much?",
  "Feeling tired or having little energy?",
  "Poor appetite or overeating?",
  "Feeling bad about yourself or that you are a failure or have let yourself or your family down?",
  "Trouble concentrating on things, such as reading the newspaper or watching television?",
  "Moving or speaking so slowly that other people could have noticed? Or the opposite?",
  "Thoughts that you would be better off dead, or of hurting yourself?",
  "Feeling nervous, anxious, or on edge?",
  "Not being able to stop or control worrying?",
  "Worrying too much about different things?",
  "Trouble relaxing?",
  "Being so restless that it is hard to sit still?",
  "Becoming easily annoyed or irritable?"
];

const OPTIONS = [
  { label: "Not at all", value: 0 },
  { label: "Several days", value: 1 },
  { label: "More than half the days", value: 2 },
  { label: "Nearly every day", value: 3 }
];

const Assessment: React.FC = () => {
  const navigate = useNavigate();
  const [step, setStep] = useState(0); 
  const [answers, setAnswers] = useState<number[]>(Array(15).fill(-1));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string>('');

  const handleSelect = (value: number) => {
    const newAnswers = [...answers];
    newAnswers[step - 1] = value;
    setAnswers(newAnswers);
    setErrorMsg(''); 
  };

  const handleNext = async () => {
    if (step < 15) {
      setStep(step + 1);
    } else {
      setIsSubmitting(true);
      setErrorMsg('');

      if (answers.includes(-1)) {
        setErrorMsg("Please answer all questions before submitting.");
        setIsSubmitting(false);
        return;
      }

      const totalScore = answers.reduce((a, b) => a + (b === -1 ? 0 : b), 0);
      const normalizedScore = Math.round((totalScore / 45) * 100);
      const riskLevel = normalizedScore > 60 ? 'High' : normalizedScore > 30 ? 'Moderate' : 'Low';

      try {
        const userStr = localStorage.getItem('user') || sessionStorage.getItem('user');
        if (!userStr) {
          throw new Error("Authentication error: User not found. Please log in again.");
        }
        const user = JSON.parse(userStr);

        const response = await fetch('http://localhost:8083/api/assessments', {
          method: 'POST',
          headers: { 
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            email: user.email,
            assessmentType: "Smart Triage",
            clinicalScore: normalizedScore,
            riskLevel: riskLevel,
            status: "Pending" 
          })
        });

        if (!response.ok) {
          throw new Error(`Server Error: Failed to save assessment (${response.status})`);
        }

        setStep(16); 
      } catch (err: any) {
        console.error("Submission failed:", err);
        setErrorMsg(err.message || "Failed to connect to the server. Please try again.");
      } finally {
        setIsSubmitting(false);
      }
    }
  };

  const totalScore = answers.reduce((a, b) => a + (b === -1 ? 0 : b), 0);
  const normalizedScore = Math.round((totalScore / 45) * 100);
  const riskLevel = normalizedScore > 60 ? 'High' : normalizedScore > 30 ? 'Moderate' : 'Low';

  if (step === 0) {
    return (
      <div className="assessment-container">
        <div className="assessment-card landing-card">
          <div className="landing-graphic">
            <div className="brain-icon">🧠</div>
            <div className="graphic-line"></div>
          </div>
          <h1 className="assessment-title">Smart Triage<br/>Assessment</h1>
          <hr className="divider" />
          <div className="info-box">
            This assessment uses evidence-based questions to calculate your mental health risk level. 
            The process takes approximately 5-10 minutes and provides personalized recommendations based 
            on your responses. All information is confidential and secure.
          </div>
          <button className="btn-primary" onClick={() => setStep(1)}>Start Quiz</button>
          <div className="secure-badge">🔒 Confidential & Secure</div>
        </div>
      </div>
    );
  }

  if (step === 16) {
    return (
      <div className="assessment-container">
        <div className="assessment-card results-card">
          <h2 className="results-title">Assessment Results</h2>
          <div className="risk-label">CALCULATED RISK LEVEL</div>
          <div className="risk-value">{riskLevel} <span className="dot">•</span></div>
          
          <div className="score-box">
            <div className="score-label">CLINICAL SCORE</div>
            <div className="score-number">{normalizedScore}</div>
            <div className="score-max">out of 100</div>
          </div>

          <div className="recommendation-box">
            <strong>Clinical Recommendation</strong>
            <p>Based on your assessment results, we strongly recommend seeking professional support. 
            Your responses indicate a level of distress that would benefit from clinical intervention. 
            A licensed mental health professional can provide personalized treatment options tailored to your specific needs.</p>
            <div className="disclaimer">
              <strong>Important:</strong> This assessment is not a diagnostic tool. Only a qualified healthcare provider can provide an official diagnosis and treatment plan.
            </div>
          </div>

          <div className="results-actions">
            <button className="btn-primary" onClick={() => navigate('/therapists')}>Book a Therapist</button>
            <button className="btn-outline" onClick={() => navigate('/emergency')}>Trigger Emergency Map</button>
          </div>
        </div>
      </div>
    );
  }

  const currentQ = step - 1;
  const progress = (step / 15) * 100;

  return (
    <div className="assessment-container">
      <div className="assessment-quiz-wrapper">
        <div className="progress-bar-container">
          <div className="progress-bar-fill" style={{ width: `${progress}%` }}></div>
        </div>
        <div className="progress-text">{Math.round(progress)}% Complete</div>

        <h2 className="quiz-question">{QUESTIONS[currentQ]}</h2>

        <div className="quiz-options">
          {OPTIONS.map((opt) => (
            <button 
              key={opt.value}
              className={`quiz-option-btn ${answers[currentQ] === opt.value ? 'selected' : ''}`}
              onClick={() => handleSelect(opt.value)}
            >
              {opt.label}
            </button>
          ))}
        </div>

        {errorMsg && (
          <div style={{ color: '#dc2626', background: '#fef2f2', padding: '10px', borderRadius: '6px', marginBottom: '20px', fontSize: '14px', textAlign: 'center', border: '1px solid #f87171' }}>
            {errorMsg}
          </div>
        )}

        <div className="quiz-footer">
          <button className="btn-outline" onClick={() => setStep(step - 1)}>Back</button>
          <button 
            className="btn-primary" 
            disabled={answers[currentQ] === -1 || isSubmitting}
            onClick={handleNext}
          >
            {isSubmitting ? 'Saving to Database...' : 'Next'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default Assessment;