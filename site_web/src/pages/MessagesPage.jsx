import { useCallback, useEffect, useRef, useState } from "react";
import { ArrowLeft, Link2, Send } from "lucide-react";
import { Link } from "react-router-dom";
import Layout from "../components/Layout";
import { ErrorState, Loader } from "../components/LoadState";
import { fetchChatGroups, fetchMessages, markChatRead, sendMessage } from "../api/opsApi";
import { useRealtime } from "../realtime/RealtimeProvider";
import { ROLE_LABEL, timeOf } from "../utils/status";

/** Enlace a la operación referida por un mensaje (mesa o pedido). */
function refLink(message) {
  if (message.refType === "TABLE") return { to: `/piso?mesa=${message.refId}`, label: "Ver mesa" };
  if (message.refType === "ORDER") return { to: `/piso?pedido=${message.refId}`, label: `Ver pedido #${message.refId}` };
  return null;
}

export default function MessagesPage() {
  const [groups, setGroups] = useState(null);
  const [error, setError] = useState("");
  const [activeId, setActiveId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState("");
  const bottom = useRef(null);

  const loadGroups = useCallback(async () => {
    try {
      setGroups(await fetchChatGroups());
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }, []);

  const loadMessages = useCallback(async (groupId) => {
    if (!groupId) return;
    try {
      const list = await fetchMessages(groupId);
      setMessages(list);
      await markChatRead(groupId);
      loadGroups();
    } catch (err) {
      setSendError(err.message);
    }
  }, [loadGroups]);

  useEffect(() => {
    loadGroups();
  }, [loadGroups]);

  useEffect(() => {
    setMessages([]);
    setSendError("");
    loadMessages(activeId);
  }, [activeId, loadMessages]);

  useEffect(() => {
    bottom.current?.scrollIntoView?.({ block: "end" });
  }, [messages]);

  useRealtime(["chat", "groups"], (event) => {
    if (event.topic === "chat" && event.refId && event.refId !== activeId) return loadGroups();
    loadGroups();
    if (activeId) loadMessages(activeId);
  });

  const active = groups?.find((g) => g.groupId === activeId) || null;

  const send = async (event) => {
    event.preventDefault();
    if (!text.trim() || !activeId) return;
    setSending(true);
    setSendError("");
    try {
      const message = await sendMessage(activeId, { body: text.trim() });
      setMessages((list) => [...list, message]);
      setText("");
    } catch (err) {
      setSendError(err.message);
    } finally {
      setSending(false);
    }
  };

  return (
    <Layout subtitle="Comunicación interna" title="Mensajes">
      {!groups ? (
        error ? <ErrorState message={error} onRetry={loadGroups} /> : <Loader label="Cargando conversaciones..." />
      ) : groups.length === 0 ? (
        <div className="zone-band py-12 text-center text-sm text-zinc-400">
          No perteneces a ningún grupo todavía. El gerente puede agregarte desde Gestión › Grupos.
        </div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-[300px_1fr]">
          <ul className={`zone-band space-y-1 p-2 ${active ? "hidden lg:block" : ""}`} aria-label="Grupos">
            {groups.map((g) => (
              <li key={g.groupId}>
                <button
                  type="button"
                  onClick={() => setActiveId(g.groupId)}
                  className={`flex w-full items-center justify-between gap-2 px-3 py-3 text-left hover:bg-white/[.04] ${g.groupId === activeId ? "bg-white/[.06]" : ""}`}
                >
                  <span className="min-w-0">
                    <span className="block truncate text-sm font-semibold text-champagne">{g.name}</span>
                    <span className="block truncate text-xs text-zinc-500">
                      {g.lastMessage ? `${g.lastSender}: ${g.lastMessage}` : `${g.members} integrantes`}
                    </span>
                  </span>
                  {g.unread > 0 && (
                    <span className="grid h-6 min-w-6 place-items-center rounded-full bg-gold px-1.5 text-xs font-bold text-carbon">{g.unread}</span>
                  )}
                </button>
              </li>
            ))}
          </ul>

          <section className={`zone-band flex min-h-[60vh] flex-col ${active ? "" : "hidden lg:flex"}`}>
            {!active ? (
              <p className="m-auto text-sm text-zinc-400">Elige una conversación.</p>
            ) : (
              <>
                <header className="mb-3 flex items-center gap-2 border-b border-white/10 pb-3">
                  <button type="button" onClick={() => setActiveId(null)} className="grid h-9 w-9 place-items-center lg:hidden" aria-label="Volver a grupos">
                    <ArrowLeft className="h-5 w-5" />
                  </button>
                  <h3 className="text-lg font-semibold text-champagne">{active.name}</h3>
                  <span className="text-xs text-zinc-500">{active.members} integrantes</span>
                </header>
                <div className="flex-1 space-y-3 overflow-y-auto pr-1" aria-live="polite">
                  {messages.length === 0 && <p className="text-sm text-zinc-500">Aún no hay mensajes.</p>}
                  {messages.map((m) => {
                    const link = refLink(m);
                    return (
                      <div key={m.id} className={`flex ${m.mine ? "justify-end" : "justify-start"}`}>
                        <div className={`max-w-[85%] border px-3 py-2 text-sm ${m.mine ? "border-gold/40 bg-gold/10" : "border-white/10 bg-white/[.04]"}`}>
                          {!m.mine && (
                            <p className="text-xs text-gold">
                              {m.senderName} · {ROLE_LABEL[m.senderRole] || m.senderRole}
                            </p>
                          )}
                          {/* Texto plano: React lo escapa, nunca se interpreta como HTML. */}
                          <p className="whitespace-pre-wrap break-words text-zinc-100">{m.body}</p>
                          {link && (
                            <Link to={link.to} className="mt-1 inline-flex items-center gap-1 text-xs text-gold underline">
                              <Link2 className="h-3 w-3" /> {link.label}
                            </Link>
                          )}
                          <p className="mt-1 text-right text-[10px] text-zinc-500">{timeOf(m.createdAt)}</p>
                        </div>
                      </div>
                    );
                  })}
                  <div ref={bottom} />
                </div>
                {sendError && <p className="mt-2 text-sm text-red-300">{sendError}</p>}
                {active.chatEnabled ? (
                  <form onSubmit={send} className="mt-3 flex gap-2 border-t border-white/10 pt-3">
                    <input
                      value={text}
                      onChange={(e) => setText(e.target.value)}
                      maxLength={1000}
                      placeholder="Escribe un mensaje..."
                      className="field flex-1"
                      aria-label="Mensaje"
                    />
                    <button type="submit" disabled={sending || !text.trim()} className="btn btn-primary bg-metal" aria-label="Enviar">
                      <Send className="h-4 w-4" />
                    </button>
                  </form>
                ) : (
                  <p className="mt-3 text-xs text-zinc-500">La mensajería de este grupo está desactivada.</p>
                )}
              </>
            )}
          </section>
        </div>
      )}
    </Layout>
  );
}
