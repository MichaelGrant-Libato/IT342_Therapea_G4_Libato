import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { SidebarLayout } from '../../core/components/SidebarLayout';
import './Messages.css';

interface UserData {
  email: string;
  fullName: string;
  role: string;
}

interface Contact {
  email: string;
  name: string;
  role: string;
  profilePictureUrl?: string;
}

interface Message {
  id?: number | string;
  senderEmail: string;
  receiverEmail: string;
  content: string;
  timestamp?: string;
  optimistic?: boolean; // flag so we can drop it when server version arrives
}

const API_BASE = 'http://localhost:8083';

const Messages: React.FC = () => {
  const navigate = useNavigate();

  const [user,            setUser]            = useState<UserData | null>(null);
  const [contacts,        setContacts]        = useState<Contact[]>([]);
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null);
  const [messages,        setMessages]        = useState<Message[]>([]);
  const [newMessage,      setNewMessage]      = useState('');
  const [isSending,       setIsSending]       = useState(false);
  const [searchQuery,     setSearchQuery]     = useState('');
  const [unreadMap,       setUnreadMap]       = useState<Record<string, boolean>>({});

  // Keep a ref so pollData always sees the latest selectedContact
  // without needing it in the dependency array (which would restart the interval).
  const selectedContactRef = useRef<Contact | null>(null);
  selectedContactRef.current = selectedContact;

  const messagesEndRef = useRef<HTMLDivElement>(null);
  useEffect(() => { messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  // ── 1. Load user + contacts ───────────────────────────────────────────────
  useEffect(() => {
    const init = async () => {
      const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
      if (!stored) { navigate('/login', { replace: true }); return; }

      const parsedUser: UserData = JSON.parse(stored);
      setUser(parsedUser);

      try {
        if (parsedUser.role === 'DOCTOR') {
          const res  = await fetch(`${API_BASE}/api/patients/doctor?email=${encodeURIComponent(parsedUser.email)}`);
          const data = await res.json();

          if (data.success) {
            setContacts(data.patients
              .filter((p: any) => !!p.email)
              .map((p: any) => ({
                email:             p.email,
                name:              p.name ?? p.fullName ?? 'Patient',
                role:              'PATIENT',
                profilePictureUrl: p.profilePictureUrl ?? '',
              }))
            );
          }
        } else {
          const res  = await fetch(`${API_BASE}/api/appointments/user?email=${encodeURIComponent(parsedUser.email)}`);
          const data = await res.json();

          if (data.success) {
            const uniqueDocs = new Map<string, Contact>();
            data.appointments.forEach((apt: any) => {
              if (apt.providerEmail && !uniqueDocs.has(apt.providerEmail)) {
                uniqueDocs.set(apt.providerEmail, {
                  email:             apt.providerEmail,
                  name:              apt.providerName ?? 'Provider',
                  role:              'DOCTOR',
                  profilePictureUrl: apt.providerProfilePictureUrl ?? '',
                });
              }
            });
            setContacts(Array.from(uniqueDocs.values()));
          }
        }
      } catch (err) {
        console.error('Failed to load contacts:', err);
      }
    };

    init();
  }, [navigate]);

  // ── 2. Poll — fixed race condition + optimistic dedup ────────────────────
  const pollData = useCallback(async (currentUser: UserData, currentContacts: Contact[]) => {
    if (!currentUser || currentContacts.length === 0) return;

    const active = selectedContactRef.current;
    const newUnread: Record<string, boolean> = {};

    // Fetch each thread independently — active thread result stored separately
    // so parallel resolution of other threads can't overwrite it.
    let activeChatMessages: Message[] = [];

    await Promise.all(currentContacts.map(async (contact) => {
      if (!contact.email) return;
      try {
        const res  = await fetch(
          `${API_BASE}/api/messages?user1=${encodeURIComponent(currentUser.email)}&user2=${encodeURIComponent(contact.email)}`
        );
        const data = await res.json();

        if (data.success && Array.isArray(data.messages) && data.messages.length > 0) {
          const msgs: Message[] = data.messages;
          const last            = msgs[msgs.length - 1];

          if (active?.email === contact.email) {
            // ── Active thread: store separately, mark read ──────────────
            activeChatMessages = msgs;
            localStorage.setItem(`last_read_${contact.email}`, new Date().toISOString());
            newUnread[contact.email] = false;
          } else if (last.senderEmail === contact.email) {
            // ── Background thread: check if newer than last read ────────
            const lastRead = localStorage.getItem(`last_read_${contact.email}`);
            const lastTime = new Date(last.timestamp ?? 0).getTime();
            const readTime = lastRead ? new Date(lastRead).getTime() : 0;
            newUnread[contact.email] = lastTime > readTime;
          } else {
            newUnread[contact.email] = false;
          }
        } else {
          newUnread[contact.email] = false;
        }
      } catch {
        // silent — don't flood console on every 3s tick
      }
    }));

    // Update active thread messages — drop any optimistic entries whose
    // content already appears in the server response (dedup by content+sender).
    if (active) {
      setMessages(prev => {
        const serverContents = new Set(
          activeChatMessages.map(m => `${m.senderEmail}:${m.content}`)
        );
        const pendingOptimistic = prev.filter(
          m => m.optimistic && !serverContents.has(`${m.senderEmail}:${m.content}`)
        );
        return [...activeChatMessages, ...pendingOptimistic];
      });
    }

    setUnreadMap(prev => {
      const changed = JSON.stringify(prev) !== JSON.stringify(newUnread);
      return changed ? newUnread : prev;
    });
  }, []);

  // Start polling once user + contacts are available
  useEffect(() => {
    if (!user || contacts.length === 0) return;

    pollData(user, contacts);
    const id = setInterval(() => pollData(user, contacts), 3000);
    return () => clearInterval(id);
  }, [user, contacts, pollData]);

  // ── 3. Send ───────────────────────────────────────────────────────────────
  const handleSend = async () => {
    if (!newMessage.trim() || !user || !selectedContact?.email) return;

    const text = newMessage.trim();

    // Optimistic insert — flagged so poll can drop it once server confirms
    const optimisticId: string = `opt_${Date.now()}`;
    const optimistic: Message  = {
      id:            optimisticId,
      senderEmail:   user.email,
      receiverEmail: selectedContact.email,
      content:       text,
      timestamp:     new Date().toISOString(),
      optimistic:    true,
    };

    setMessages(prev => [...prev, optimistic]);
    setNewMessage('');
    setIsSending(true);
    localStorage.setItem(`last_read_${selectedContact.email}`, new Date().toISOString());

    try {
      const res = await fetch(`${API_BASE}/api/messages/send`, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({
          senderEmail:   user.email,
          receiverEmail: selectedContact.email,
          content:       text,
        }),
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      // Immediately poll so the server-confirmed message replaces the optimistic one
      await pollData(user, contacts);

    } catch (err) {
      console.error('Failed to send message:', err);
      // Remove the optimistic entry on failure
      setMessages(prev => prev.filter(m => m.id !== optimisticId));
    } finally {
      setIsSending(false);
    }
  };

  const handleContactClick = (contact: Contact) => {
    if (!contact.email) return;
    setSelectedContact(contact);
    setMessages([]); // clear while new thread loads
    localStorage.setItem(`last_read_${contact.email}`, new Date().toISOString());
    setUnreadMap(prev => ({ ...prev, [contact.email]: false }));
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) handleSend();
  };

  const getInitials = (name: string) =>
    name ? name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : '?';

  const filteredContacts = contacts.filter(c =>
    c.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  if (!user) return null;

  return (
    <SidebarLayout title="Messages">
      <div className="msg-container">

        {/* ── Sidebar ── */}
        <div className="msg-sidebar">
          <div className="msg-search-box">
            <input
              type="text"
              placeholder="Search contacts..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
            />
          </div>

          <div className="msg-contact-list">
            {filteredContacts.length === 0 ? (
              <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
                No active contacts found.
              </div>
            ) : (
              filteredContacts.map((contact, idx) => {
                const isUnread   = !!unreadMap[contact.email];
                const isSelected = selectedContact?.email === contact.email;

                return (
                  <div
                    key={contact.email || idx}
                    className={`msg-contact ${isSelected ? 'active' : ''} ${!contact.email ? 'disabled' : ''}`}
                    onClick={() => handleContactClick(contact)}
                    style={{ opacity: contact.email ? 1 : 0.6, cursor: contact.email ? 'pointer' : 'not-allowed' }}
                  >
                    <div className="msg-avatar" style={{ overflow: 'hidden', padding: 0 }}>
                      {contact.profilePictureUrl ? (
                        <img src={contact.profilePictureUrl} alt={contact.name}
                          style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      ) : getInitials(contact.name)}
                    </div>

                    <div className="msg-info" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
                      <div>
                        <div className="msg-name-time">
                          <h4 style={{ margin: 0, fontWeight: isUnread ? 800 : 500, color: isUnread ? '#1a1a2e' : 'inherit' }}>
                            {contact.name}
                          </h4>
                        </div>
                        {contact.email
                          ? <p style={{ fontSize: 11, color: '#0A5C36', margin: 0 }}>{contact.email}</p>
                          : <p style={{ color: '#ef4444', fontWeight: 'bold', fontSize: 11, margin: 0 }}>Missing Email!</p>
                        }
                      </div>
                      {isUnread && (
                        <div style={{ width: 10, height: 10, backgroundColor: '#ef4444', borderRadius: '50%' }} />
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* ── Chat area ── */}
        <div className="msg-chat-area">
          {selectedContact ? (
            <>
              <div className="msg-chat-header">
                <div className="msg-avatar" style={{ overflow: 'hidden', padding: 0 }}>
                  {selectedContact.profilePictureUrl ? (
                    <img src={selectedContact.profilePictureUrl} alt={selectedContact.name}
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : getInitials(selectedContact.name)}
                </div>
                <div>
                  <h3 style={{ margin: 0, fontSize: 16 }}>{selectedContact.name}</h3>
                  <p style={{ margin: 0, fontSize: 12, color: 'var(--text-muted)' }}>
                    {selectedContact.role === 'DOCTOR' ? 'Licensed Therapist' : 'Patient'}
                  </p>
                </div>
              </div>

              <div className="msg-history">
                {messages.length === 0 ? (
                  <div style={{ textAlign: 'center', color: 'var(--text-muted)', margin: 'auto' }}>
                    <p>Start the conversation with {selectedContact.name}</p>
                  </div>
                ) : (
                  messages.map((msg, i) => {
                    const isSentByMe = msg.senderEmail === user.email;
                    return (
                      <div key={msg.id ?? i} className={`msg-bubble ${isSentByMe ? 'sent' : 'received'}`}
                        style={{ opacity: msg.optimistic ? 0.6 : 1 }}>
                        {msg.content}
                        <div style={{ fontSize: 10, marginTop: 4, opacity: 0.7, textAlign: isSentByMe ? 'right' : 'left' }}>
                          {msg.timestamp
                            ? new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                            : 'Sending…'}
                        </div>
                      </div>
                    );
                  })
                )}
                <div ref={messagesEndRef} />
              </div>

              <div className="msg-input-area">
                <input
                  type="text"
                  placeholder="Type a secure message..."
                  value={newMessage}
                  onChange={e => setNewMessage(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={isSending || !selectedContact.email}
                />
                <button
                  onClick={handleSend}
                  disabled={!newMessage.trim() || isSending || !selectedContact.email}
                >
                  {isSending ? '…' : 'Send'}
                </button>
              </div>
            </>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)' }}>
              <h3>Select a conversation</h3>
              <p>Choose a contact from the sidebar to start chatting.</p>
            </div>
          )}
        </div>
      </div>
    </SidebarLayout>
  );
};

export default Messages;