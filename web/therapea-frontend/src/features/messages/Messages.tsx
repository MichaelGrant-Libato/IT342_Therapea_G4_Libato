import React, { useState, useEffect, useRef } from 'react';
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
}

const Messages: React.FC = () => {
  const navigate = useNavigate();
  const [user, setUser] = useState<UserData | null>(null);
  
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [selectedContact, setSelectedContact] = useState<Contact | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [newMessage, setNewMessage] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  
  // Track which contacts have unread messages
  const [unreadMap, setUnreadMap] = useState<Record<string, boolean>>({});

  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  // 1. Fetch User and Contacts
  useEffect(() => {
    const init = async () => {
      const stored = localStorage.getItem('user') || sessionStorage.getItem('user');
      if (!stored) { navigate('/login', { replace: true }); return; }
      
      const parsedUser = JSON.parse(stored);
      setUser(parsedUser);

      try {
        if (parsedUser.role === 'DOCTOR') {
          const res = await fetch(`http://localhost:8083/api/patients/doctor?email=${encodeURIComponent(parsedUser.email)}`);
          const data = await res.json();
          if (data.success) {
            const mappedContacts = data.patients.map((p: any) => ({
              email: p.email || '', 
              name: p.name,
              role: 'PATIENT',
              profilePictureUrl: p.profilePictureUrl 
            }));
            setContacts(mappedContacts);
          }
        } else {
          const res = await fetch(`http://localhost:8083/api/appointments/user?email=${encodeURIComponent(parsedUser.email)}`);
          const data = await res.json();
          if (data.success) {
            const uniqueDocs = new Map();
            data.appointments.forEach((apt: any) => {
              if (apt.providerEmail && !uniqueDocs.has(apt.providerEmail)) {
                uniqueDocs.set(apt.providerEmail, {
                  email: apt.providerEmail, 
                  name: apt.providerName,
                  role: 'DOCTOR',
                  profilePictureUrl: apt.providerProfilePictureUrl 
                });
              }
            });
            setContacts(Array.from(uniqueDocs.values()));
          }
        }
      } catch (err) {
        console.error("Failed to load contacts:", err);
      }
    };
    init();
  }, [navigate]);

  // 2. Real-Time Fetching for Active Chat & Updating Read Status
  useEffect(() => {
    if (!user || contacts.length === 0) return;

    const pollData = async () => {
      const newUnreadMap: Record<string, boolean> = {};
      let currentChatMessages: Message[] = [];

      await Promise.all(contacts.map(async (contact) => {
        if (!contact.email) return;
        try {
          const res = await fetch(`http://localhost:8083/api/messages?user1=${encodeURIComponent(user.email)}&user2=${encodeURIComponent(contact.email)}`);
          const data = await res.json();
          
          if (data.success && data.messages.length > 0) {
            const msgs = data.messages;
            const lastMsg = msgs[msgs.length - 1];

            // If we are actively chatting with this contact, log it as read automatically
            if (selectedContact?.email === contact.email) {
              currentChatMessages = msgs;
              localStorage.setItem(`last_read_${contact.email}`, new Date().toISOString());
              newUnreadMap[contact.email] = false;
            } 
            // If the last message is from them and we aren't viewing it, check read status
            else if (lastMsg.senderEmail === contact.email) {
              const lastRead = localStorage.getItem(`last_read_${contact.email}`);
              if (!lastRead || new Date(lastMsg.timestamp || Date.now()) > new Date(lastRead)) {
                newUnreadMap[contact.email] = true;
              } else {
                newUnreadMap[contact.email] = false;
              }
            }
          }
        } catch (err) {
          // Silent catch to prevent console flooding
        }
      }));

      if (selectedContact) {
        setMessages(currentChatMessages);
      }

      setUnreadMap(prev => {
        const changed = JSON.stringify(prev) !== JSON.stringify(newUnreadMap);
        return changed ? newUnreadMap : prev;
      });
    };

    pollData(); // initial fetch
    const intervalId = setInterval(pollData, 3000);
    return () => clearInterval(intervalId);
  }, [user, contacts, selectedContact]);

  // 3. Send Message
  const handleSend = async () => {
    if (!newMessage.trim() || !user || !selectedContact || !selectedContact.email) return;

    const dbPayload = {
      senderEmail: user.email,
      receiverEmail: selectedContact.email,
      content: newMessage.trim()
    };

    const uiMessage: Message = {
      id: Date.now(), 
      senderEmail: user.email,
      receiverEmail: selectedContact.email,
      content: newMessage.trim(),
      timestamp: new Date().toISOString()
    };

    setMessages(prev => [...prev, uiMessage]);
    setNewMessage('');
    setIsSending(true);
    
    // Auto-mark as read since we just sent a message
    localStorage.setItem(`last_read_${selectedContact.email}`, new Date().toISOString());

    try {
      const res = await fetch('http://localhost:8083/api/messages/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(dbPayload)
      });
      if (!res.ok) throw new Error("API failed");
    } catch (err) {
      console.error("Failed to send message", err);
    } finally {
      setIsSending(false);
    }
  };

  const handleContactClick = (contact: Contact) => {
    if (!contact.email) return;
    
    // Set as active
    setSelectedContact(contact);
    
    // Mark as read immediately when clicked to remove the bold text and red dot
    localStorage.setItem(`last_read_${contact.email}`, new Date().toISOString());
    setUnreadMap(prev => ({ ...prev, [contact.email]: false }));
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') handleSend();
  };

  const getInitials = (name: string) => {
    return name ? name.split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase() : '?';
  };

  const filteredContacts = contacts.filter(c => c.name.toLowerCase().includes(searchQuery.toLowerCase()));

  if (!user) return null;

  return (
    <SidebarLayout title="Messages">
      <div className="msg-container">
        
        <div className="msg-sidebar">
          <div className="msg-search-box">
            <input 
              type="text" 
              placeholder="Search contacts..." 
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          
          <div className="msg-contact-list">
            {filteredContacts.length === 0 ? (
              <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
                No active contacts found.
              </div>
            ) : (
              filteredContacts.map((contact, idx) => {
                const isUnread = unreadMap[contact.email];

                return (
                  <div 
                    key={contact.email || idx} 
                    className={`msg-contact ${selectedContact?.email === contact.email ? 'active' : ''} ${!contact.email ? 'disabled' : ''}`}
                    onClick={() => handleContactClick(contact)}
                    style={{ opacity: contact.email ? 1 : 0.6, cursor: contact.email ? 'pointer' : 'not-allowed' }}
                  >
                    <div className="msg-avatar" style={{ overflow: 'hidden', padding: 0 }}>
                      {contact.profilePictureUrl ? (
                        <img src={contact.profilePictureUrl} alt={contact.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                      ) : (
                        getInitials(contact.name)
                      )}
                    </div>

                    <div className="msg-info" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
                      <div>
                        <div className="msg-name-time">
                          {/* Apply Bold styling if unread */}
                          <h4 style={{ margin: 0, fontWeight: isUnread ? 800 : 500, color: isUnread ? '#1a1a2e' : 'inherit' }}>
                            {contact.name}
                          </h4>
                        </div>
                        {contact.email ? (
                           <p style={{ fontSize: '11px', color: '#0A5C36', margin: 0 }}>{contact.email}</p>
                        ) : (
                           <p style={{ color: '#ef4444', fontWeight: 'bold', fontSize: '11px', margin: 0 }}>Missing Email!</p>
                        )}
                      </div>
                      
                      {/* Red dot indicator for unread messages within the internal list */}
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

        <div className="msg-chat-area">
          {selectedContact ? (
            <>
              <div className="msg-chat-header">
                <div className="msg-avatar" style={{ overflow: 'hidden', padding: 0 }}>
                  {selectedContact.profilePictureUrl ? (
                    <img src={selectedContact.profilePictureUrl} alt={selectedContact.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : (
                    getInitials(selectedContact.name)
                  )}
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
                  <div style={{ textAlign: 'center', color: 'var(--text-muted)', marginTop: 'auto', marginBottom: 'auto' }}>
                    <p>Start the conversation with {selectedContact.name}</p>
                  </div>
                ) : (
                  messages.map(msg => {
                    const isSentByMe = msg.senderEmail === user.email;
                    return (
                      <div key={msg.id} className={`msg-bubble ${isSentByMe ? 'sent' : 'received'}`}>
                        {msg.content}
                        <div style={{ fontSize: 10, marginTop: 4, opacity: 0.7, textAlign: isSentByMe ? 'right' : 'left' }}>
                          {msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Sending...'}
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
                  onChange={(e) => setNewMessage(e.target.value)}
                  onKeyDown={handleKeyDown}
                  disabled={isSending || !selectedContact.email}
                />
                <button onClick={handleSend} disabled={!newMessage.trim() || isSending || !selectedContact.email}>
                  {isSending ? '...' : 'Send'}
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