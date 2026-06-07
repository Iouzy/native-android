// Tab: HOJE — intenções do dia + reflexão noturna

function TabHoje({ store, accentColor, onJumpToPauta }) {
  const { state, addIntention, updateIntention, toggleIntention, removeIntention, setReflection, setDayReflection, carryOverIntentions, toggleHabitToday, incHabitDay } = store;
  const { today, blocks, habits } = state;

  // Unfinished intentions from the most recent archived day — offered as a
  // one-tap carry-over so momentum survives the midnight rollover.
  const carry = useMemo(() => {
    const days = state.days || {};
    const keys = Object.keys(days).filter(k => k < today.dayKey).sort();
    for (let i = keys.length - 1; i >= 0; i--) {
      const items = (days[keys[i]].intentions || []).filter(it => !it.done && (it.text || "").trim());
      if (items.length) return { key: keys[i], items };
    }
    return null;
  }, [state.days, today.dayKey]);
  const [adding, setAdding] = useState(false);
  const [newText, setNewText] = useState("");
  const [newPriority, setNewPriority] = useState(2); // default to "média"
  const [newTarget, setNewTarget] = useState(""); // minutes as a free string; "" = none
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyDayKey, setHistoryDayKey] = useState(null);

  const totalFocusToday = useMemo(() => {
    const key = dayKeyOf(Date.now());
    return blocks
      .filter(b => dayKeyOf(b.createdAt) === key)
      .reduce((acc, b) => acc + blockFocusMs(b), 0);
  }, [blocks]);

  const focusByIntention = useMemo(() => {
    const map = {};
    for (const b of blocks) {
      if (b.linkedToId) {
        map[b.linkedToId] = (map[b.linkedToId] || 0) + blockFocusMs(b);
      }
    }
    return map;
  }, [blocks]);

  const pastKeys = useMemo(() => pastDayKeys(state), [state.days]);

  // The list auto-sorts by priority level (1 = highest); unset priorities sink to
  // the bottom. A stable sort keeps insertion order within the same level, so
  // there's no separate manual-reorder mode — the number IS the order.
  const displayIntentions = useMemo(() => {
    return today.intentions
      .map((it, i) => [it, i])
      .sort((a, b) => ((a[0].priority || 4) - (b[0].priority || 4)) || (a[1] - b[1]))
      .map(pair => pair[0]);
  }, [today.intentions]);

  // Today's tides — the actionable slice of Marés surfaced in Hoje (daily always;
  // weekly/monthly only on an eligible, still-open day). The source of truth is
  // the shared habitDayStatus() in the store, so this strip never diverges from
  // the Marés grid; the full grid/history/tiers stay in the Marés tab. /
  // A fatia acionável das Marés trazida para o Hoje.
  const todayTides = useMemo(() => {
    const tk = dayKeyOf(Date.now());
    const out = [];
    for (const h of (habits || [])) {
      const st = habitDayStatus(h, tk);
      if (st) out.push({ habit: h, ...st });
    }
    return out;
  }, [habits]);

  // Day pulse — mirrors the home-screen widget. Respiros stay OUT of the tides
  // denominator: an honest rest isn't a miss. / Pulso do dia.
  const intTotal = today.intentions.length;
  const intDone = today.intentions.filter(i => i.done).length;
  const tideDone = todayTides.filter(t => t.state === "done").length;
  const tideDenom = todayTides.filter(t => t.state !== "respiro").length;

  // Render today's summary as a shareable PNG (Web Share, else download).
  const shareDay = () => {
    const done = today.intentions.filter(i => i.done).length;
    window.shareDayCard({
      dateLabel: fmtDateLong(Date.now()),
      focusValue: totalFocusToday > 0 ? fmtDuration(totalFocusToday) : "—",
      focusCaption: tr("em foco hoje"),
      ratioValue: done + " / " + today.intentions.length,
      ratioCaption: tr("intenções concluídas"),
      tagline: tr("escrito à mão, todos os dias"),
      accent: accentColor,
    });
    if (window.haptic) window.haptic(8);
  };

  const resetForm = () => { setNewText(""); setNewPriority(2); setNewTarget(""); setAdding(false); };
  const commitNew = () => {
    if (newText.trim()) {
      const tm = parseInt(newTarget, 10);
      addIntention(newText, {
        priority: newPriority,
        targetMin: Number.isFinite(tm) && tm > 0 ? tm : null,
      });
    }
    resetForm();
  };

  return (
    <div className="scroll" style={{ flex: 1, overflowY: "auto", padding: "8px 24px 40px", position: "relative", zIndex: 1 }}>
      {/* Header */}
      <div style={{ paddingTop: 16, paddingBottom: 24, display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 14 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontFamily: "var(--mono)", fontSize: 10, letterSpacing: "0.18em", textTransform: "uppercase", color: "var(--ink-3)", marginBottom: 6 }}>
            {fmtDateLong(Date.now())}
          </div>
          <h1 style={{ fontFamily: "var(--serif)", fontSize: 44, lineHeight: 1.0, margin: 0, fontWeight: 400, letterSpacing: "-0.015em" }}>
            {tr("O que importa")} <em style={{ color: accentColor }}>{tr("hoje")}</em>?
          </h1>
          {(() => {
            // One quiet line tying the three tabs together: intentions done,
            // focus logged, tides marked — each shown only when it has a value,
            // so an empty day stays blank. / Pulso do dia (intenções · foco · marés).
            const parts = [];
            if (intTotal > 0) parts.push(trf("{d}/{t} intenções", { d: intDone, t: intTotal }));
            if (totalFocusToday > 0) parts.push(trf("{d} em foco", { d: fmtDuration(totalFocusToday) }));
            if (tideDenom > 0) parts.push(trf("{d}/{t} marés", { d: tideDone, t: tideDenom }));
            if (parts.length === 0) return null;
            return (
              <div style={{ marginTop: 10, fontFamily: "var(--mono)", fontSize: 11, color: "var(--ink-3)", letterSpacing: "0.02em" }}>
                {parts.join("   ·   ")}
              </div>
            );
          })()}
        </div>
        <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 8, flexShrink: 0, marginTop: 2 }}>
          <button onClick={() => setHistoryOpen(true)} className="tap" title={tr("ver dias anteriores")}
            style={{
              border: "1px solid var(--rule)", background: "transparent",
              borderRadius: 8, padding: "6px 10px",
              fontFamily: "var(--mono)", fontSize: 9, letterSpacing: "0.14em",
              textTransform: "uppercase", color: "var(--ink-3)", cursor: "pointer",
            }}>
            {tr("dias anteriores")} ↗
          </button>
          <button onClick={shareDay} className="tap" title={tr("partilhar o dia")} aria-label={tr("partilhar o dia")}
            style={{
              border: "1px solid var(--rule)", background: "transparent",
              borderRadius: 8, padding: "6px 9px", color: "var(--ink-3)", cursor: "pointer",
              display: "inline-flex", alignItems: "center", gap: 6,
              fontFamily: "var(--mono)", fontSize: 9, letterSpacing: "0.14em", textTransform: "uppercase",
            }}>
            <Icon.Upload size={12}/> {tr("partilhar")}
          </button>
        </div>
      </div>

      {/* Add new intention — now at the TOP of the list (swapped with the
          carry-over button, which moved to the bottom). Tapping the button
          opens an inline form for name + priority + optional duration. */}
      {adding ? (
        <IntentionForm
          text={newText} onText={setNewText}
          priority={newPriority} onPriority={setNewPriority}
          target={newTarget} onTarget={setNewTarget}
          onCommit={commitNew} onCancel={resetForm}
          accentColor={accentColor}
        />
      ) : (
        <button onClick={() => setAdding(true)} className="tap"
          data-tour="add-intention"
          style={{
            marginTop: 4, marginBottom: 4, background: "transparent", border: "none",
            padding: "10px 0", display: "flex", alignItems: "center", gap: 10,
            color: "var(--ink-3)", fontSize: 14, cursor: "pointer",
          }}>
          <div style={{
            width: 22, height: 22, borderRadius: "50%",
            border: "1.5px dashed var(--ink-3)",
            display: "flex", alignItems: "center", justifyContent: "center",
          }}>
            <Icon.Plus size={12}/>
          </div>
          {tr("adicionar intenção")}
        </button>
      )}

      {/* Empty-state hint */}
      {today.intentions.length === 0 && !adding && (
        <div style={{ padding: "16px 0 8px", textAlign: "left" }}>
          <div style={{
            fontFamily: "var(--serif)", fontStyle: "italic", fontSize: 18,
            color: "var(--ink-3)", lineHeight: 1.4,
          }}>
            {tr("Comece por listar 1 a 4 coisas que importam hoje.")}<br/>
            {tr("Não tarefas de rotina — coisas que")} <em>{tr("movem")}</em> {tr("o seu dia.")}
          </div>
          <StarterChips
            label={tr("Para começar")}
            accentColor={accentColor}
            items={["Ler 20 minutos", "Caminhar 30 min", "Escrever 3 ideias", "Uma conversa importante"]}
            onPick={(text) => addIntention(text, { priority: 2 })}
          />
        </div>
      )}

      <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
        {displayIntentions.map((it) => (
          <IntentionRow
            key={it.id}
            intention={it}
            focusMs={focusByIntention[it.id] || 0}
            onToggle={() => toggleIntention(it.id)}
            onChange={text => updateIntention(it.id, { text })}
            onRemove={() => removeIntention(it.id)}
            onSetPriority={v => updateIntention(it.id, { priority: v })}
            onSetTarget={v => updateIntention(it.id, { targetMin: v })}
            onStart={() => onJumpToPauta && onJumpToPauta({ intention: it, start: true })}
            accentColor={accentColor}
          />
        ))}
      </div>

      {/* Carry-over — now at the BOTTOM (swapped with the add button). Always
          available when the previous day left unfinished intentions, not just
          on an empty list. */}
      {carry && (
        <button onClick={() => carryOverIntentions(carry.items)} className="tap"
          style={{
            marginTop: 16, width: "100%", textAlign: "left", cursor: "pointer",
            display: "flex", alignItems: "center", gap: 12,
            background: "var(--paper-2)", border: "1px solid var(--rule)",
            borderRadius: 12, padding: "12px 14px", color: "var(--ink)",
          }}>
          <span style={{ flexShrink: 0, color: accentColor, display: "inline-flex" }}><Icon.Plus size={16}/></span>
          <span style={{ flex: 1, minWidth: 0 }}>
            <span style={{ display: "block", fontSize: 14, fontWeight: 500 }}>
              {carry.items.length === 1
                ? tr("Trazer 1 intenção de ontem")
                : trf("Trazer {n} intenções de ontem", { n: carry.items.length })}
            </span>
            <span style={{ display: "block", fontSize: 12.5, color: "var(--ink-3)", marginTop: 2, fontStyle: "italic", fontFamily: "var(--serif)" }}>
              {carry.items.slice(0, 2).map(i => i.text).join(" · ")}{carry.items.length > 2 ? "…" : ""}
            </span>
          </span>
        </button>
      )}

      {/* Marés de hoje — the actionable tides for today, surfaced from the Marés
          tab. Tapping writes through the same store actions (toggle / increment);
          the full grid, history and tiers stay in Marés. Placed between intentions
          and the night reflection so Hoje reads as one nested day: now → today's
          rhythm → the night. / A fatia de hoje das Marés. */}
      {todayTides.length > 0 && (
        <div style={{ marginTop: 36 }}>
          <div style={{
            fontFamily: "var(--mono)", fontSize: 9, letterSpacing: "0.2em",
            textTransform: "uppercase", color: "var(--ink-3)", marginBottom: 10,
            display: "flex", justifyContent: "space-between", alignItems: "baseline",
          }}>
            <span>{tr("Marés de hoje")}</span>
            {tideDenom > 0 && (
              <span style={{ letterSpacing: "0.06em", color: "var(--ink-4)" }}>{tideDone}/{tideDenom}</span>
            )}
          </div>
          <div style={{ display: "flex", flexDirection: "column" }}>
            {todayTides.map((t, i) => (
              <TodayTideRow
                key={t.habit.id}
                tide={t}
                last={i === todayTides.length - 1}
                accentColor={t.habit.color || accentColor}
                onAct={t.state === "respiro" ? null : () => {
                  if (window.haptic) window.haptic(8);
                  if (t.isCount) incHabitDay(t.habit.id, dayKeyOf(Date.now()));
                  else toggleHabitToday(t.habit.id);
                }}
              />
            ))}
          </div>
        </div>
      )}

      {/* Evening reflection */}
      <div style={{ marginTop: 40, padding: "24px 22px", background: "var(--paper-2)", borderRadius: 14, border: "1px solid var(--rule)" }}>
        <div style={{ fontFamily: "var(--mono)", fontSize: 9, letterSpacing: "0.2em", textTransform: "uppercase", color: "var(--ink-3)", marginBottom: 8 }}>
          {tr("Reflexão da noite")}
        </div>
        <div style={{ fontFamily: "var(--serif)", fontStyle: "italic", fontSize: 18, color: "var(--ink-2)", marginBottom: 12 }}>
          "{tr("O que valeu hoje?")}"
        </div>
        <AutoTextarea
          value={today.reflection}
          onChange={setReflection}
          placeholder={tr("Escreva quando quiser. Não precisa de ser longo.")}
          minRows={2}
          style={{
            fontSize: 15, lineHeight: 1.5, color: "var(--ink)",
            fontFamily: "var(--serif)",
          }}
        />
      </div>

      {/* Quarterly goals */}
      <GoalsSection store={store} accentColor={accentColor}/>

      <div style={{
        marginTop: 32, fontFamily: "var(--mono)", fontSize: 10,
        color: "var(--ink-4)", letterSpacing: "0.04em", textAlign: "center",
      }}>
        {tr("amanhã, recomeça.")}
      </div>

      {/* History sheet */}
      <HojeHistorySheet
        open={historyOpen}
        onClose={() => { setHistoryOpen(false); setHistoryDayKey(null); }}
        days={state.days || {}}
        blocks={blocks}
        keys={pastKeys}
        openedDayKey={historyDayKey}
        onOpenDay={setHistoryDayKey}
        setDayReflection={setDayReflection}
        accentColor={accentColor}
      />
    </div>
  );
}

// ─── History sheet (past days) ─────────────────────────────
// Small toggle chip for the history filters (status / priority). /
// Chip de filtro do histórico.
function HistChip({ label, active, accentColor, onClick }) {
  return (
    <button onClick={onClick} className="tap" aria-pressed={active}
      style={{
        border: "1px solid " + (active ? accentColor : "var(--rule)"),
        background: active ? `${accentColor}14` : "var(--paper-2)",
        color: active ? accentColor : "var(--ink-2)",
        borderRadius: 999, padding: "5px 11px", cursor: "pointer",
        fontFamily: "var(--mono)", fontSize: 10.5, letterSpacing: "0.04em",
        display: "inline-flex", alignItems: "center", gap: 4,
      }}>{label}</button>
  );
}

function HojeHistorySheet({ open, onClose, days, blocks, keys, openedDayKey, onOpenDay, setDayReflection, accentColor }) {
  const [query, setQuery] = useState("");
  // Extra filters narrow the day list as history grows: by intention status
  // (done / unfinished) and by priority level. / Filtros por estado e prioridade.
  const [statusFilter, setStatusFilter] = useState(null); // null | "done" | "open"
  const [prioFilter, setPrioFilter] = useState(null);     // null | 1 | 2 | 3
  useEffect(() => { if (!open) { setQuery(""); setStatusFilter(null); setPrioFilter(null); } }, [open]);
  if (!open) return null;
  const opened = openedDayKey && days[openedDayKey];

  const q = query.trim().toLowerCase();
  const hasFilters = !!(q || statusFilter || prioFilter);
  // Text matches day-wide (reflection or any intention); status/priority match a
  // day that has at least one intention satisfying BOTH active constraints.
  const filteredKeys = !hasFilters ? keys : keys.filter(k => {
    const d = days[k];
    const ints = d.intentions || [];
    const textOK = !q
      || (d.reflection || "").toLowerCase().includes(q)
      || ints.some(i => (i.text || "").toLowerCase().includes(q));
    const attrOK = (!statusFilter && !prioFilter) || ints.some(i =>
      (!statusFilter || (statusFilter === "done" ? !!i.done : !i.done)) &&
      (!prioFilter || (i.priority || 4) === prioFilter));
    return textOK && attrOK;
  });

  return (
    <Sheet open={open} onClose={onClose} title={tr("Dias anteriores")}>
      <div style={{ padding: "8px 24px 24px" }}>
        {opened ? (
          <HojeHistoryDetail
            dayKey={openedDayKey}
            day={opened}
            blocks={blocks}
            accentColor={accentColor}
            setDayReflection={setDayReflection}
            onBack={() => onOpenDay(null)}
          />
        ) : (
          <>
            <div style={{
              fontFamily: "var(--serif)", fontSize: 20, lineHeight: 1.25,
              color: "var(--ink)", marginBottom: 4, letterSpacing: "-0.01em",
            }}>
              {tr("O que importou nos dias anteriores.")}
            </div>
            <div style={{
              fontFamily: "var(--serif)", fontStyle: "italic", fontSize: 13,
              color: "var(--ink-3)", marginBottom: 14,
            }}>
              {tr("As intenções e a reflexão de cada dia ficam guardadas. Toque para reler.")}
            </div>
            {keys.length > 0 && (
              <input value={query} onChange={e => setQuery(e.target.value)}
                placeholder={tr("procurar nas reflexões e intenções…")}
                style={{
                  width: "100%", border: "1px solid var(--rule)", background: "var(--paper-2)",
                  borderRadius: 10, padding: "10px 14px", fontSize: 14, color: "var(--ink)",
                  marginBottom: 10, fontFamily: "var(--sans)",
                }}/>
            )}
            {keys.length > 0 && (
              <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginBottom: 16, alignItems: "center" }}>
                <HistChip label={tr("concluídas")}
                  active={statusFilter === "done"} accentColor={accentColor}
                  onClick={() => setStatusFilter(s => s === "done" ? null : "done")}/>
                <HistChip label={tr("por concluir")}
                  active={statusFilter === "open"} accentColor={accentColor}
                  onClick={() => setStatusFilter(s => s === "open" ? null : "open")}/>
                <span style={{ width: 1, height: 16, background: "var(--rule)", margin: "0 2px" }}/>
                {PRIO_LEVELS.map(p => (
                  <HistChip key={p.value} label={<>{p.mark} {tr(p.label)}</>}
                    active={prioFilter === p.value} accentColor={accentColor}
                    onClick={() => setPrioFilter(v => v === p.value ? null : p.value)}/>
                ))}
                {hasFilters && (
                  <button onClick={() => { setQuery(""); setStatusFilter(null); setPrioFilter(null); }}
                    className="tap" title={tr("limpar filtros")} aria-label={tr("limpar filtros")}
                    style={{
                      marginLeft: "auto", background: "transparent", border: "none",
                      color: "var(--ink-3)", cursor: "pointer", fontFamily: "var(--mono)",
                      fontSize: 10, letterSpacing: "0.08em", textTransform: "uppercase",
                    }}>{tr("limpar filtros")} ✕</button>
                )}
              </div>
            )}
            {keys.length === 0 ? (
              <div style={{
                padding: "32px 0", textAlign: "center",
                fontFamily: "var(--serif)", fontStyle: "italic", fontSize: 14,
                color: "var(--ink-3)",
              }}>
                {tr("Ainda não há dias arquivados.")}<br/>
                {tr("Volte aqui amanhã.")}
              </div>
            ) : filteredKeys.length === 0 ? (
              <div style={{
                padding: "28px 0", textAlign: "center",
                fontFamily: "var(--serif)", fontStyle: "italic", fontSize: 14,
                color: "var(--ink-3)",
              }}>
                {q ? trf('Nada encontrado para "{q}".', { q: query.trim() }) : tr("Nada encontrado com estes filtros.")}
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {filteredKeys.map(k => {
                  const d = days[k];
                  const total = d.intentions.length;
                  const done = d.intentions.filter(i => i.done).length;
                  const focusMs = dailyFocusMs(blocks, k);
                  return (
                    <button key={k} onClick={() => onOpenDay(k)} className="tap"
                      style={{
                        textAlign: "left", border: "1px solid var(--rule)",
                        background: "var(--paper)", borderRadius: 10,
                        padding: "12px 14px", cursor: "pointer",
                        display: "flex", flexDirection: "column", gap: 6,
                      }}>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 10 }}>
                        <div style={{
                          fontFamily: "var(--serif)", fontSize: 18, color: "var(--ink)",
                          letterSpacing: "-0.01em",
                        }}>
                          {fmtDateLong(tsFromDayKey(k))}
                        </div>
                        <div style={{
                          fontFamily: "var(--mono)", fontSize: 10, color: "var(--ink-3)",
                          letterSpacing: "0.06em", flexShrink: 0,
                        }}>
                          {total > 0 && <>{done === 1 ? trf("{done}/{total} feito", { done, total }) : trf("{done}/{total} feitos", { done, total })}</>}
                          {focusMs > 0 && total > 0 && " · "}
                          {focusMs > 0 && trf("{d} foco", { d: fmtDuration(focusMs) })}
                        </div>
                      </div>
                      {d.reflection && d.reflection.trim() && (
                        <div style={{
                          fontFamily: "var(--serif)", fontStyle: "italic", fontSize: 13,
                          color: "var(--ink-2)", lineHeight: 1.4,
                          overflow: "hidden", display: "-webkit-box",
                          WebkitLineClamp: 2, WebkitBoxOrient: "vertical",
                        }}>
                          "{d.reflection.trim()}"
                        </div>
                      )}
                    </button>
                  );
                })}
              </div>
            )}
          </>
        )}
      </div>
    </Sheet>
  );
}

function HojeHistoryDetail({ dayKey, day, blocks, accentColor, setDayReflection, onBack }) {
  const focusMs = dailyFocusMs(blocks, dayKey);
  const blocksDay = blocks.filter(b => b.sessions.some(s => dayKeyOf(s.startedAt) === dayKey));
  return (
    <div>
      <button onClick={onBack} className="tap"
        style={{
          display: "inline-flex", alignItems: "center", gap: 7,
          border: "1px solid var(--rule)", background: "var(--paper-2)",
          borderRadius: 999, padding: "9px 15px 9px 11px",
          fontFamily: "var(--mono)", fontSize: 12, letterSpacing: "0.08em",
          color: "var(--ink-2)", textTransform: "uppercase", cursor: "pointer",
          marginBottom: 16,
        }}>
        <span style={{ fontSize: 17, lineHeight: 1, marginTop: -2 }}>‹</span>
        {tr("dias")}
      </button>
      <div style={{
        fontFamily: "var(--mono)", fontSize: 9, letterSpacing: "0.16em",
        textTransform: "uppercase", color: "var(--ink-3)", marginBottom: 4,
      }}>
        {fmtDateLong(tsFromDayKey(dayKey))}
      </div>
      <div style={{
        fontFamily: "var(--serif)", fontSize: 24, lineHeight: 1.15,
        color: "var(--ink)", letterSpacing: "-0.01em", marginBottom: 18,
      }}>
        {tr("O que importou nesse dia.")}
      </div>

      {day.intentions.length === 0 ? (
        <div style={{
          fontFamily: "var(--serif)", fontStyle: "italic", fontSize: 14,
          color: "var(--ink-3)", marginBottom: 18,
        }}>
          {tr("Sem intenções registadas.")}
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", marginBottom: 18 }}>
          {day.intentions.map((it, i) => (
            <div key={it.id} style={{
              display: "flex", alignItems: "flex-start", gap: 12,
              padding: "10px 0", borderBottom: i < day.intentions.length - 1 ? "1px solid var(--rule)" : "none",
            }}>
              <div style={{
                width: 18, height: 18, borderRadius: "50%",
                border: `1.5px solid ${it.done ? accentColor : "var(--ink-3)"}`,
                background: it.done ? accentColor : "transparent",
                flexShrink: 0, marginTop: 2,
                display: "flex", alignItems: "center", justifyContent: "center",
              }}>
                {it.done && (
                  <svg width="9" height="9" viewBox="0 0 10 10" fill="none">
                    <path d="M2 5.5L4 7.5L8 3" stroke="var(--paper)" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                )}
              </div>
              <div style={{
                flex: 1, fontSize: i === 0 ? 18 : 15,
                fontFamily: i === 0 ? "var(--serif)" : "var(--sans)",
                color: it.done ? "var(--ink-3)" : "var(--ink)",
                textDecoration: it.done ? "line-through" : "none",
                lineHeight: 1.3,
              }}>
                {it.text || <em style={{ color: "var(--ink-3)" }}>{tr("(intenção sem texto)")}</em>}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Reflection is editable here too, so a past day can be revisited or a
          forgotten note added later. Wired to the live store via setDayReflection. */}
      <div style={{
        padding: "16px 18px", background: "var(--paper-2)",
        borderRadius: 12, border: "1px solid var(--rule)",
        marginBottom: 16,
      }}>
        <div style={{
          fontFamily: "var(--mono)", fontSize: 9, letterSpacing: "0.2em",
          textTransform: "uppercase", color: "var(--ink-3)", marginBottom: 8,
        }}>
          {tr("reflexão da noite")}
        </div>
        <AutoTextarea
          value={day.reflection || ""}
          onChange={text => setDayReflection(dayKey, text)}
          placeholder={tr("Escreva quando quiser. Não precisa de ser longo.")}
          minRows={2}
          style={{
            fontSize: 16, lineHeight: 1.5, color: "var(--ink)",
            fontFamily: "var(--serif)",
          }}
        />
      </div>

      {focusMs > 0 && (
        <div style={{
          padding: "12px 14px", border: "1px solid var(--rule)",
          borderRadius: 10, display: "flex", justifyContent: "space-between",
          alignItems: "center",
        }}>
          <div>
            <div style={{
              fontFamily: "var(--mono)", fontSize: 9, letterSpacing: "0.16em",
              textTransform: "uppercase", color: "var(--ink-3)", marginBottom: 3,
            }}>
              {tr("tempo em foco")}
            </div>
            <div style={{
              fontFamily: "var(--serif)", fontSize: 22, color: accentColor,
              letterSpacing: "-0.01em",
            }}>
              {fmtDuration(focusMs)}
            </div>
          </div>
          <div style={{
            fontFamily: "var(--mono)", fontSize: 10, color: "var(--ink-3)",
            letterSpacing: "0.06em", textAlign: "right",
          }}>
            {blocksDay.length} {blocksDay.length === 1 ? tr("bloco") : tr("blocos")}
          </div>
        </div>
      )}
    </div>
  );
}

// ─── Intention row ─────────────────────────────────────────
// Touch devices have no hover. The row's quick-focus (▷) and delete (🗑) used to
// reveal on hover only, so on a phone they were invisible — and a tap leaves a
// "stuck" hover on the last-touched row, which is why they showed up on a single
// row at random. Reveal them always on coarse pointers; keep the tidy
// hover-reveal for mouse/trackpad.
const COARSE_POINTER = typeof window !== "undefined" && typeof window.matchMedia === "function"
  ? window.matchMedia("(hover: none), (pointer: coarse)").matches
  : false;

// Priority levels (1 = highest). The Hoje list auto-sorts by this number, so the
// number IS the order — there's no manual drag-reorder mode anymore. Picking the
// priority replaces the old principal/importante labels + the reorder handle,
// which together were redundant.
const PRIO_LEVELS = [
  { value: 1, label: "alta",  mark: "●" },
  { value: 2, label: "média", mark: "◆" },
  { value: 3, label: "baixa", mark: "○" },
];
function prioColor(value, accentColor) {
  return value === 1 ? accentColor : value === 2 ? "var(--ink-2)" : "var(--ink-4)";
}
function prioMeta(value) {
  return PRIO_LEVELS.find(o => o.value === value) || PRIO_LEVELS[2];
}

// ─── Inline add form: name + priority + optional duration ───
// Opens in place (no bottom sheet) so adding an intention captures everything in
// one step instead of add-then-tweak-chips.
function FieldLabel({ children }) {
  return (
    <div style={{
      fontFamily: "var(--mono)", fontSize: 9, letterSpacing: "0.16em",
      textTransform: "uppercase", color: "var(--ink-3)", marginBottom: 8,
    }}>{children}</div>
  );
}

function PrioritySelect({ value, onSet, accentColor }) {
  return (
    <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
      {PRIO_LEVELS.map(opt => {
        const active = opt.value === value;
        const col = prioColor(opt.value, accentColor);
        return (
          <button key={opt.value} onClick={() => onSet(opt.value)} className="tap"
            style={{
              border: `1px solid ${active ? accentColor + "55" : "var(--rule)"}`,
              background: active ? accentColor + "14" : "transparent",
              borderRadius: 999, padding: "7px 13px", cursor: "pointer",
              fontFamily: "var(--mono)", fontSize: 11, letterSpacing: "0.04em",
              color: active ? col : "var(--ink-3)", fontWeight: active ? 600 : 400,
              display: "inline-flex", alignItems: "center", gap: 6, whiteSpace: "nowrap",
            }}>
            <span style={{ fontSize: 9, color: col }}>{opt.mark}</span>
            {opt.value} · {tr(opt.label)}
          </button>
        );
      })}
    </div>
  );
}

// Free-entry duration field (no fixed 25/50/90 presets). Empty = no planned time.
function DurationField({ value, onChange }) {
  return (
    <div style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
      <span style={{ fontSize: 12, color: "var(--ink-3)" }}>◷</span>
      <input
        type="number" min="1" max="480" inputMode="numeric"
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder="—"
        style={{
          width: 70, border: "1px solid var(--rule)", background: "transparent",
          borderRadius: 999, padding: "7px 10px", textAlign: "center",
          fontFamily: "var(--mono)", fontSize: 12, color: "var(--ink)",
        }}/>
      <span style={{ fontFamily: "var(--mono)", fontSize: 11, color: "var(--ink-3)" }}>min</span>
    </div>
  );
}

function IntentionForm({ text, onText, priority, onPriority, target, onTarget, onCommit, onCancel, accentColor }) {
  const ready = !!text.trim();
  return (
    <div style={{
      marginTop: 4, marginBottom: 10, padding: "16px 18px",
      background: "var(--paper-2)", border: "1px solid var(--rule)", borderRadius: 14,
    }}>
      <input autoFocus value={text}
        onChange={e => onText(e.target.value)}
        onKeyDown={e => {
          if (e.key === "Enter") { e.preventDefault(); onCommit(); }
          if (e.key === "Escape") { e.preventDefault(); onCancel(); }
        }}
        placeholder={tr("Nova intenção…")}
        style={{
          width: "100%", border: "none", background: "transparent", padding: 0,
          fontFamily: "var(--serif)", fontSize: 19, color: "var(--ink)", lineHeight: 1.3,
        }}/>
      <div style={{ marginTop: 16 }}>
        <FieldLabel>{tr("prioridade")}</FieldLabel>
        <PrioritySelect value={priority} onSet={onPriority} accentColor={accentColor}/>
      </div>
      <div style={{ marginTop: 14 }}>
        <FieldLabel>{tr("duração (opcional)")}</FieldLabel>
        <DurationField value={target} onChange={onTarget}/>
      </div>
      <div style={{ marginTop: 18, display: "flex", justifyContent: "flex-end", gap: 8 }}>
        <button onClick={onCancel} className="tap"
          style={{
            border: "1px solid var(--rule)", background: "transparent",
            borderRadius: 999, padding: "8px 16px", cursor: "pointer",
            fontFamily: "var(--mono)", fontSize: 11, letterSpacing: "0.04em", color: "var(--ink-3)",
          }}>{tr("cancelar")}</button>
        <button onClick={onCommit} disabled={!ready} className="tap"
          style={{
            border: "none", borderRadius: 999, padding: "8px 18px",
            cursor: ready ? "pointer" : "default",
            background: ready ? accentColor : "var(--rule)",
            color: ready ? "var(--paper)" : "var(--ink-4)",
            fontFamily: "var(--mono)", fontSize: 11, letterSpacing: "0.04em", fontWeight: 600,
          }}>{tr("adicionar")}</button>
      </div>
    </div>
  );
}

// ─── Row chips (compact, collapsible) ───
function TargetChip({ targetMin, accentColor, onSet }) {
  const [open, setOpen] = useState(false);
  const [custom, setCustom] = useState("");

  const set = targetMin > 0;
  const chipStyle = (active) => ({
    border: `1px solid ${active ? accentColor + "55" : "var(--rule)"}`,
    background: active ? accentColor + "14" : "transparent",
    borderRadius: 999, padding: "6px 11px", cursor: "pointer",
    fontFamily: "var(--mono)", fontSize: 10, letterSpacing: "0.04em",
    color: active ? accentColor : "var(--ink-3)", fontWeight: active ? 600 : 400,
    whiteSpace: "nowrap",
  });

  if (open) {
    const confirm = () => {
      const n = parseInt(custom, 10);
      onSet(n > 0 && n <= 480 ? n : null);
      setOpen(false); setCustom("");
    };
    return (
      <div style={{ display: "flex", gap: 4, alignItems: "center" }}>
        <input
          type="number" min="1" max="480" inputMode="numeric"
          value={custom}
          onChange={e => setCustom(e.target.value)}
          onKeyDown={e => { if (e.key === "Enter") confirm(); if (e.key === "Escape") { setOpen(false); setCustom(""); } }}
          onBlur={confirm}
          placeholder="min"
          autoFocus
          style={{
            width: 64, border: "1px solid var(--rule)", background: "transparent",
            borderRadius: 999, padding: "6px 8px",
            fontFamily: "var(--mono)", fontSize: 10, color: "var(--ink)", textAlign: "center",
          }}/>
        <button onMouseDown={(e) => e.preventDefault()}
          onClick={() => { onSet(null); setOpen(false); setCustom(""); }} className="tap"
          style={{ ...chipStyle(false), padding: "6px 10px", color: "var(--ink-4)" }}>×</button>
      </div>
    );
  }

  return (
    <button onClick={() => { setCustom(set ? String(targetMin) : ""); setOpen(true); }} className="tap" title={tr("definir duração")}
      style={{
        ...chipStyle(set),
        display: "inline-flex", alignItems: "center", gap: 5,
      }}>
      <span style={{ fontSize: 9 }}>◷</span>{set ? trf("{n} min", { n: targetMin }) : tr("duração")}
    </button>
  );
}

function PriorityChip({ priority, accentColor, onSet }) {
  const [open, setOpen] = useState(false);
  const cur = prioMeta(priority);
  const curCol = prioColor(cur.value, accentColor);

  if (open) {
    return (
      <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
        {PRIO_LEVELS.map(opt => {
          const active = opt.value === priority;
          const col = prioColor(opt.value, accentColor);
          return (
            <button key={opt.value}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => { onSet(opt.value); setOpen(false); }} className="tap"
              style={{
                border: `1px solid ${active ? accentColor + "55" : "var(--rule)"}`,
                background: active ? accentColor + "14" : "transparent",
                borderRadius: 999, padding: "6px 11px", cursor: "pointer",
                fontFamily: "var(--mono)", fontSize: 10, letterSpacing: "0.04em",
                color: col, fontWeight: active ? 600 : 400,
                display: "inline-flex", alignItems: "center", gap: 5, whiteSpace: "nowrap",
              }}>
              <span style={{ fontSize: 9 }}>{opt.mark}</span>{opt.value} · {tr(opt.label)}
            </button>
          );
        })}
      </div>
    );
  }

  return (
    <button onClick={() => setOpen(true)} className="tap" title={tr("definir prioridade")}
      style={{
        border: "1px solid var(--rule)", background: "transparent",
        borderRadius: 999, padding: "6px 11px", cursor: "pointer",
        fontFamily: "var(--mono)", fontSize: 10, letterSpacing: "0.04em",
        color: priority ? curCol : "var(--ink-3)", fontWeight: priority ? 600 : 400,
        display: "inline-flex", alignItems: "center", gap: 5, whiteSpace: "nowrap",
      }}>
      {priority
        ? <><span style={{ fontSize: 9 }}>{cur.mark}</span>{cur.value} · {tr(cur.label)}</>
        : <><span style={{ fontSize: 9 }}>○</span>{tr("prioridade")}</>}
    </button>
  );
}

function IntentionRow({ intention, focusMs, onToggle, onChange, onRemove, onSetPriority, onSetTarget, onStart, accentColor }) {
  const [hover, setHover] = useState(false);
  const isPrimary = intention.priority === 1;
  const isImportant = intention.priority === 2;
  return (
    <div
      onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{
        display: "flex", alignItems: "flex-start", gap: 10,
        padding: "14px 0", borderBottom: "1px solid var(--rule)",
        transition: "background 0.12s",
      }}>
      <div style={{ paddingTop: 1 }}>
        <Check checked={intention.done} onChange={onToggle} accentColor={accentColor}/>
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <EditableText
          value={intention.text}
          onChange={onChange}
          placeholder={tr("(intenção sem texto)")}
          multiline={false}
          style={{
            display: "block",
            fontFamily: isPrimary || isImportant ? "var(--serif)" : "var(--sans)",
            fontSize: isPrimary ? 22 : isImportant ? 18 : 16,
            lineHeight: 1.25,
            color: intention.done ? "var(--ink-3)" : "var(--ink)",
            textDecoration: intention.done ? "line-through" : "none",
            textDecorationColor: "var(--ink-3)",
            letterSpacing: isPrimary ? "-0.01em" : "0",
          }}
        />
        <div style={{ marginTop: 8, display: "flex", alignItems: "center", flexWrap: "wrap", gap: 8, fontFamily: "var(--mono)", fontSize: 10, color: "var(--ink-3)", letterSpacing: "0.04em" }}>
          <PriorityChip priority={intention.priority} accentColor={accentColor} onSet={onSetPriority}/>
          <TargetChip targetMin={intention.targetMin} accentColor={accentColor} onSet={onSetTarget}/>
          {focusMs > 0 && <span style={{ marginLeft: 2 }}>{trf("{d} em foco", { d: fmtDuration(focusMs) })}</span>}
        </div>
      </div>
      <div style={{ display: "flex", gap: 6, alignItems: "center", flexShrink: 0, opacity: (hover || COARSE_POINTER) ? 1 : 0, transition: "opacity 0.12s" }}>
        <button onClick={onStart} className="tap" title={tr("focar nesta intenção agora")} aria-label={tr("focar nesta intenção agora")}
          style={{ width: 36, height: 36, borderRadius: 9, border: "1px solid var(--rule)", background: "transparent", color: accentColor, display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer" }}>
          <Icon.Play size={14}/>
        </button>
        <button onClick={onRemove} className="tap" title={tr("remover intenção")} aria-label={tr("remover intenção")}
          style={{ width: 36, height: 36, borderRadius: 9, border: "1px solid var(--rule)", background: "transparent", color: "var(--ink-3)", display: "flex", alignItems: "center", justifyContent: "center", cursor: "pointer" }}>
          <Icon.Trash size={15}/>
        </button>
      </div>
    </div>
  );
}

// ─── Today's tide row (Marés de hoje strip) ───
// One actionable tide surfaced in Hoje. The indicator + name form a single tap
// target that marks the tide done — or increments a countable one — through the
// store. A respiro day renders muted and non-interactive (onAct == null): editing
// a respiro stays in the Marés tab. / Linha de uma maré de hoje.
function TodayTideRow({ tide, last, accentColor, onAct }) {
  const h = tide.habit;
  const { state } = tide;
  const done = state === "done";
  const respiro = state === "respiro";
  const partial = state === "partial";
  const base = {
    display: "flex", alignItems: "center", gap: 12,
    padding: "11px 0", width: "100%", textAlign: "left",
    borderBottom: last ? "none" : "1px solid var(--rule)",
  };
  const inner = (
    <>
      <span style={{
        width: 24, height: 24, borderRadius: "50%", flexShrink: 0,
        border: `1.6px solid ${done ? accentColor : respiro ? "var(--ink-4)" : "var(--ink-3)"}`,
        background: done ? accentColor : "transparent",
        display: "flex", alignItems: "center", justifyContent: "center", overflow: "hidden",
      }}>
        {done && <Icon.Check size={13} color="var(--paper)"/>}
        {respiro && <span style={{ width: 9, height: 2, borderRadius: 2, background: "var(--ink-4)" }}/>}
        {partial && <span style={{ fontFamily: "var(--mono)", fontSize: 9, fontWeight: 600, color: accentColor }}>{tide.count}</span>}
      </span>
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{
          display: "block", fontSize: 15, lineHeight: 1.25,
          color: done || respiro ? "var(--ink-3)" : "var(--ink)",
          textDecoration: done ? "line-through" : "none", textDecorationColor: "var(--ink-3)",
        }}>{h.name}</span>
        {(h.time || h.clock || respiro) && (
          <span style={{ display: "block", fontFamily: "var(--serif)", fontStyle: "italic", fontSize: 12.5, color: "var(--ink-3)", marginTop: 2 }}>
            {respiro ? tr("respiro") : (
              <>
                {h.clock && <span style={{ fontFamily: "var(--mono)", fontStyle: "normal", fontSize: 11, marginRight: h.time ? 6 : 0 }}>{h.clock}</span>}
                {h.time}
              </>
            )}
          </span>
        )}
      </span>
      {tide.isCount && (
        <span style={{ flexShrink: 0, fontFamily: "var(--mono)", fontSize: 10, letterSpacing: "0.04em", color: done ? accentColor : "var(--ink-3)" }}>
          {tide.count}/{tide.target}{h.unit ? " " + h.unit : ""}
        </span>
      )}
    </>
  );
  if (!onAct) return <div style={base}>{inner}</div>;
  return (
    <button onClick={onAct} className="tap" aria-pressed={done}
      aria-label={trf("marcar maré: {h}", { h: h.name })}
      style={{ ...base, border: "none", background: "transparent", cursor: "pointer", color: "inherit" }}>
      {inner}
    </button>
  );
}

window.TabHoje = TabHoje;
