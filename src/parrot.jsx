// Pauta — Pip, the parrot companion (head only).
//
// A small, opt-out mascot: just Pip's HEAD rests, discreet, in the bottom-right
// corner of the screen. Tap him and he expands out, says one line (a habit tip,
// "did you know", app hint, joke, or a tab-specific nudge), then quietly recoils
// after ~4.5s. He never auto-pops, never covers content — barely noticed until
// you reach for him. That quiet is the whole point.
//
// MOTION IS 100% JAVASCRIPT (a single requestAnimationFrame loop writing inline
// transforms/opacity to DOM nodes via refs). It deliberately does NOT use CSS
// animations/transitions: some Androids (e.g. MIUI "Remove animations" / OS
// reduce-motion) kill every CSS animation app-wide, which would leave Pip a dead
// sticker. JS-set inline transforms are immune to that. Only the spoken line and
// the expanded/collapsed state use React state; the loop mutates refs directly.
//
// Battery: the loop throttles to ~30fps while idle, fully parks when the tab is
// hidden (visibilitychange), and never starts when Pip is disabled.
//
// Content is bilingual inline (PT/EN) so it follows the language toggle without
// bloating the i18n dictionary; pl() picks. Disable: Definições → "Papagaio
// ajudante". Renders nothing when off or during onboarding. Pure front-end; no
// storage, no network.

// Pick the line for the current language (PT is the source).
function pl(msg) { return (window.PAUTA_LANG === "en" ? msg.en : msg.pt); }
// Pick a message from a pool, avoiding recently-shown ones so Pip doesn't repeat
// himself. `avoid` is a Set of the last few PT strings shown.
function pickFresh(arr, avoid) {
  const fresh = arr.filter(m => !avoid.has(m.pt));
  const pool = fresh.length ? fresh : arr;
  return pool[Math.floor(Math.random() * pool.length)];
}
function pickOne(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

// ─── Content pools ──────────────────────────────────────────
// Habit-building tips.
const PARROT_TIPS = [
  { pt: "Empilha o hábito novo a seguir a um que já fazes: «depois do café, 5 min de leitura».", en: "Stack a new habit after one you already do: “after coffee, 5 min of reading.”" },
  { pt: "Começa ridiculamente pequeno. Uma flexão conta. O resto vem depois.", en: "Start absurdly small. One push-up counts. The rest follows." },
  { pt: "Se falhares um dia, tudo bem — só não falhes dois seguidos. É a única regra.", en: "Miss a day, fine — just never miss two in a row. That's the only rule." },
  { pt: "O ambiente ganha à força de vontade. Deixa o livro na almofada.", en: "Environment beats willpower. Leave the book on your pillow." },
  { pt: "Hábitos formam-se pela repetição, não pela perfeição.", en: "Habits are built by repetition, not perfection." },
  { pt: "Torna-o óbvio: deixa a pista à vista. Ténis à porta, água na secretária.", en: "Make it obvious: leave the cue in sight. Shoes by the door, water on the desk." },
  { pt: "Liga o hábito a uma hora e a um sítio. «Quando» e «onde» quase duplicam a adesão.", en: "Tie the habit to a time and a place. “When” and “where” nearly double follow-through." },
];
// "Did you know" facts.
const PARROT_DYK = [
  { pt: "Sabias? Um hábito demora em média ~66 dias a tornar-se automático — não 21.", en: "Did you know? A habit takes ~66 days on average to become automatic — not 21." },
  { pt: "Sabias? Marcar num calendário visível aumenta a adesão. As Marés existem por isso.", en: "Did you know? Marking a visible calendar boosts follow-through. That's why Marés exists." },
  { pt: "Sabias? Os papagaios-cinzentos aprendem centenas de palavras. Eu prefiro dar dicas.", en: "Did you know? Grey parrots learn hundreds of words. I prefer giving tips." },
  { pt: "Sabias? Dormir bem é o hábito que torna todos os outros mais fáceis.", en: "Did you know? Good sleep is the habit that makes every other one easier." },
  { pt: "Sabias? Celebrar uma pequena vitória faz o cérebro querer repeti-la.", en: "Did you know? Celebrating a small win makes the brain want to repeat it." },
];
// App tips.
const PARROT_APP = [
  { pt: "Liga a cópia automática nas Definições — eu durmo mais descansado.", en: "Turn on auto-backup in Settings — I'll sleep easier." },
  { pt: "Tudo é offline e teu. Sem conta, sem servidor, sem ninguém a espreitar.", en: "Everything's offline and yours. No account, no server, nobody peeking." },
  { pt: "Tens uma revisão semanal nas Definições — vê os teus padrões sem julgamento.", en: "There's a weekly review in Settings — see your patterns, no judgement." },
  { pt: "Toca-me para outra deixa. Ou desliga-me nas Definições, se eu falar demais.", en: "Tap me for another line. Or switch me off in Settings if I talk too much." },
];
// Silly parrot / general jokes.
const PARROT_JOKES = [
  { pt: "Porque foi o papagaio às Marés? Para apanhar a onda do hábito! 🦜", en: "Why did the parrot visit Marés? To catch the habit wave! 🦜" },
  { pt: "Dizem que repito tudo. Repito: hábitos pequenos, resultados grandes.", en: "They say I repeat everything. I repeat: small habits, big results." },
  { pt: "Não sou um hábito, mas também gosto de aparecer todos os dias.", en: "I'm not a habit, but I do like showing up every day." },
  { pt: "Sou a única ave que faz code review às tuas rotinas. 🦜", en: "I'm the only bird that code-reviews your routines. 🦜" },
  { pt: "Dizem que tenho cérebro do tamanho de uma noz. Mesmo assim lembro-me da tua maré.", en: "They say my brain is the size of a walnut. I still remember your tide." },
  { pt: "Polly quer… um hábito marcado. 🦜", en: "Polly wants… a habit checked off. 🦜" },
  { pt: "Não voo em círculos — voo em rotinas.", en: "I don't fly in circles — I fly in routines." },
  { pt: "Bom dia! Ou boa tarde. Sou um papagaio, não um relógio.", en: "Good morning! Or afternoon. I'm a parrot, not a clock." },
];
// Philosophical jokes / quips.
const PARROT_PHILO = [
  { pt: "Sócrates só sabia que nada sabia. Eu sei uma coisa: sabes marcar este hábito.", en: "Socrates knew only that he knew nothing. I know one thing: you can check off this habit." },
  { pt: "Heraclito dizia que não te banhas duas vezes no mesmo rio. Mas podes marcar a mesma maré todos os dias.", en: "Heraclitus said you can't step in the same river twice. But you can mark the same tide every day." },
  { pt: "Se um hábito cai na floresta e ninguém o marca, terá existido? Marca, só para tirar a dúvida.", en: "If a habit falls in the forest and no one logs it, did it happen? Log it, just to be sure." },
  { pt: "Camus mandou imaginar Sísifo feliz. Eu imagino-te a concluir o bloco. 🪨", en: "Camus told us to imagine Sisyphus happy. I imagine you finishing the block. 🪨" },
  { pt: "«Penso, logo existo.» Marcas, logo persistes.", en: "“I think, therefore I am.” You log, therefore you persist." },
  { pt: "Os estóicos diziam: controla o que podes. Não controlas o dia inteiro — controlas o próximo toque.", en: "The Stoics said: control what you can. You can't control the whole day — only the next tap." },
  { pt: "Nietzsche falava do eterno retorno. Tu chamas-lhe hábito diário.", en: "Nietzsche spoke of eternal recurrence. You call it a daily habit." },
  { pt: "A vida é curta; a tua streak não tem de ser. 🌱", en: "Life is short; your streak doesn't have to be. 🌱" },
];
// Portuguese cultural nods.
const PARROT_CULTURE = [
  { pt: "Magalhães deu a volta ao mundo sem GPS. Tu só precisas de marcar o hábito de hoje. 🧭", en: "Magellan sailed around the world without GPS. You just need to check off today's habit. 🧭" },
  { pt: "Magalhães partiu com cinco naus e voltou uma. A tua streak, essa, só tem de continuar inteira.", en: "Magellan left with five ships and one came back. Your streak only has to stay in one piece." },
  { pt: "Fernando Pessoa era várias pessoas e aparecia todos os dias para escrever. Tu és só um — sem desculpas. 🖋️", en: "Fernando Pessoa was many people and showed up to write every day. You're just one — no excuses. 🖋️" },
  { pt: "Pessoa escreveu: «Tudo vale a pena se a alma não é pequena». A tua maré também vale.", en: "Pessoa wrote: “Everything is worth it if the soul is not small.” Your tide is worth it too." },
  { pt: "«Navegar é preciso», dizia Pessoa. Marcar hábitos também é. 🌊", en: "“To sail is necessary,” wrote Pessoa. So is checking off habits. 🌊" },
  { pt: "Lisboa tem sete colinas; tu só tens de subir um hábito de cada vez.", en: "Lisbon has seven hills; you only have to climb one habit at a time." },
  { pt: "Em Portugal, os governos caem mais depressa do que as minhas penas na muda. Que a tua rotina não caia. 🪶", en: "In Portugal, governments fall faster than my moulting feathers. May your routine not fall. 🪶" },
  { pt: "Há orçamentos que não passam na Assembleia. O teu hábito de hoje passa — é só um toque.", en: "Some budgets don't pass in parliament. Today's habit passes — it's just one tap." },
  { pt: "Mudou o governo outra vez? A tua rotina pode bem ser a coisa mais estável do país.", en: "Government changed again? Your routine might just be the most stable thing in the country." },
];

// Tab-specific lines — Pip leans toward the tab you're on.
const PARROT_HOJE = [
  { pt: "Uma a quatro intenções. Mais do que isso é uma lista de desejos, não um dia.", en: "One to four intentions. More than that is a wish list, not a day." },
  { pt: "Escolhe a intenção que, feita, faria o dia valer a pena.", en: "Pick the intention that, if done, would make the day worth it." },
  { pt: "À noite, uma linha de reflexão chega. O teu eu de amanhã agradece.", en: "At night, one line of reflection is enough. Tomorrow-you will thank you." },
  { pt: "Arrasta as intenções para pôr a mais importante no topo.", en: "Drag your intentions to put the most important one on top." },
  { pt: "O que não cabe hoje, cabe amanhã. Recomeça-se sempre.", en: "What doesn't fit today fits tomorrow. You always begin again." },
];
const PARROT_PAUTA = [
  { pt: "Começa um bloco e o tempo conta-se sozinho. Tu só tens de aparecer.", en: "Start a block and time counts itself. You just have to show up." },
  { pt: "Pausa sem culpa: a pausa também faz parte do foco.", en: "Pause without guilt: the pause is part of the focus too." },
  { pt: "Um bloco de 25 minutos vale mais do que uma hora distraída.", en: "A 25-minute block beats a distracted hour." },
  { pt: "Toca no ícone de ecrã cheio para entrar no modo foco zen.", en: "Tap the full-screen icon to enter zen focus mode." },
  { pt: "Foco não é correr mais; é parar de saltar de tarefa em tarefa.", en: "Focus isn't running faster; it's stopping the task-hopping." },
];
const PARROT_MARES = [
  { pt: "Constância vence intensidade. A maré sobe um dia de cada vez.", en: "Constancy beats intensity. The tide rises one day at a time." },
  { pt: "Um respiro honesto não quebra a maré — só um dia esquecido a faz recuar.", en: "An honest breath doesn't break the tide — only a forgotten day pulls it back." },
  { pt: "Pressão longa num dia vazio marca um respiro. Sem culpa.", en: "Long-press an empty day to mark a breath. No guilt." },
  { pt: "Toda a onda começa pequena. A tua também começou.", en: "Every wave starts small. Yours did too." },
];

// Eye-centre Y (in the head's viewBox) for the blink squash — MUST match the
// eye drawn in ParrotHead.
const HEAD_EYE_CY = 53;

// ── Pip's head: a scarlet-macaw head facing left — terracotta/accent head, a bare
// pale face with fine feather lines, a big yellow-horn hooked beak, and a dark eye
// whose pupil sits in its own <g> so the gaze can drift independently of the blink
// (which squashes the whole eye). A faint neck/shoulder hint keeps it from reading
// as a severed head. Drawn in a tight 54×54 window of a 0..88 space. The head body
// takes the live `accent` so it recolours with the theme.
function ParrotHead({ accent, size, eyeRef, pupilRef }) {
  const face   = "#F1EADA";   // bare facial skin
  const beakUp = "#E8D6AC";   // horn upper mandible
  const beakDk = "#C2AC82";   // beak shading
  const beakLo = "#6E5F50";   // darker lower mandible
  return (
    <svg width={size} height={size * 44 / 54} viewBox="16 38 54 44" fill="none" aria-hidden="true"
      style={{ display: "block", overflow: "visible" }}>
      {/* neck / shoulder hint */}
      <path d="M52 66 C62 66 70 72 72 82 L40 82 C40 74 45 67 52 66 Z" fill={accent} opacity="0.96"/>
      {/* head */}
      <circle cx="51" cy="55" r="14" fill={accent}/>
      <path d="M43 45 C50 42 59 43 63 49" stroke="rgba(255,255,255,0.18)" strokeWidth="2.2" fill="none" opacity="0.5"/>
      {/* bare pale face patch with fine feather lines (green-winged macaw) */}
      <path d="M39 50 C46 47 55 48 58 53 C59 59 55 66 47 66 C40 66 36 60 37 54 Z" fill={face}/>
      <path d="M41 53 C46 52 52 53 56 55" stroke={accent} strokeWidth="0.6" fill="none" opacity="0.4"/>
      <path d="M40 57 C46 56 51 57 55 59" stroke={accent} strokeWidth="0.6" fill="none" opacity="0.4"/>
      <path d="M41 61 C46 60 50 61 54 62" stroke={accent} strokeWidth="0.6" fill="none" opacity="0.4"/>
      {/* big hooked beak */}
      <path d="M43 50 C35 48 27 51 24 57 C23 60 26 64 30 63 C32 60 36 58 41 58 C43 57 44 53 43 50 Z" fill={beakUp}/>
      <path d="M43 50 C35 48 27 51 24 57" stroke={beakDk} strokeWidth="0.7" fill="none" opacity="0.7"/>
      <path d="M30 62 C32 65 37 65 40 62 C37 63 33 63 30 62 Z" fill={beakLo}/>
      <ellipse cx="40" cy="52" rx="1.3" ry="1" fill={beakDk} opacity="0.85"/>
      {/* eye — sclera squashes on the blink; pupil nested so the gaze can drift */}
      <g ref={eyeRef}>
        <circle cx="53" cy={HEAD_EYE_CY} r="4.3" fill="#F7F1E4"/>
        <g ref={pupilRef}>
          <circle cx="54" cy={HEAD_EYE_CY} r="2.8" fill="#2A1E14"/>
          <circle cx="55.2" cy={HEAD_EYE_CY - 1.2} r="0.9" fill="#fff"/>
        </g>
      </g>
    </svg>
  );
}

const HEAD_SIZE = 112;
// How far Pip drops below the tab-bar line while idle (px): his base hides behind
// the bar and only his face peeks up from the corner; a tap lifts him fully into
// view, base still touching the line — he never floats free of it.
const PEEK_DROP = 44;
// Fallback tab-bar height until it's measured (the overlay ends on the bar line).
const CORNER_FALLBACK = 76;

function ParrotCompanion({ store, accentColor, tab }) {
  const enabled = store.state.prefs.parrot !== false;
  const [out, setOut] = useState(false);     // expanded + talking?
  const [line, setLine] = useState(null);    // the spoken line (stays mounted; faded by the loop)
  const [bottomPx, setBottomPx] = useState(CORNER_FALLBACK);  // sits above the tab bar

  // Refs that always hold the current values, so the long-lived rAF loop never
  // reads a stale closure. // PT: refs com o valor atual.
  const tabRef = useRef(tab); tabRef.current = tab;
  const outRef = useRef(out); outRef.current = out;
  const hideTimer = useRef(null);

  // Last few PT strings shown, so a tap rarely repeats the previous line.
  const recent = useRef(new Set());
  const remember = (msg) => {
    recent.current.add(msg.pt);
    if (recent.current.size > 10) recent.current.delete(recent.current.values().next().value);
  };

  // Hide Pip while any bottom-sheet/modal is open (same reasoning as before: the
  // Pauta sheets sit in their own stacking context below this overlay, so Pip
  // would otherwise float over their fields). A MutationObserver flips this.
  const [sheetOpen, setSheetOpen] = useState(false);
  useEffect(() => {
    const check = () => setSheetOpen(!!document.querySelector(".om-sheet-card"));
    check();
    const mo = new MutationObserver(check);
    mo.observe(document.body, { childList: true, subtree: true });
    return () => mo.disconnect();
  }, []);

  // The overlay ends exactly on the tab bar's top line, so Pip is anchored to it
  // (peeks up from there, never floats) and can't spill over the tabs. Measure the
  // bar's real height (already includes its safe-area padding); re-measure on
  // resize / when the bar resizes. // PT: o overlay acaba na linha da barra.
  useEffect(() => {
    const measure = () => {
      const bar = document.querySelector(".om-tabbar");
      const h = bar ? bar.getBoundingClientRect().height : CORNER_FALLBACK;
      setBottomPx(Math.round(h));
    };
    measure();
    const bar = document.querySelector(".om-tabbar");
    const ro = (bar && "ResizeObserver" in window) ? new ResizeObserver(measure) : null;
    if (ro) ro.observe(bar);
    window.addEventListener("resize", measure);
    return () => { window.removeEventListener("resize", measure); if (ro) ro.disconnect(); };
  }, []);

  // ── DOM refs the rAF loop writes to (never React state per frame) ──
  const scaleRef  = useRef(null);   // peek ⇄ out (scale from the bottom-right corner)
  const breathRef = useRef(null);   // subtle breathing (vertical scale)
  const eyeRef    = useRef(null);   // blink
  const pupilRef  = useRef(null);   // (reserved) gaze drift
  const bubbleRef = useRef(null);   // speech bubble fade/pop

  // One mutable motion object so the loop allocates nothing per frame.
  const m = useRef({ p: 0, nextBlink: 0, blinkUntil: 0 }).current;

  // Pick a line, leaning toward the current tab's own pool.
  const pickLine = () => {
    const av = recent.current, t = tabRef.current, r = Math.random();
    if (t === "hoje"  && r < 0.5) return pickFresh(PARROT_HOJE, av);
    if (t === "pauta" && r < 0.5) return pickFresh(PARROT_PAUTA, av);
    if (t === "mares" && r < 0.5) return pickFresh(PARROT_MARES, av);
    const general = [PARROT_TIPS, PARROT_DYK, PARROT_APP, PARROT_JOKES, PARROT_PHILO, PARROT_CULTURE];
    return pickFresh(pickOne(general), av);
  };

  // Tap Pip: expand and say a (fresh) line, then auto-recoil after ~4.5s. Tapping
  // again while out just swaps the line and resets the timer.
  const speak = () => {
    const msg = pickLine();
    remember(msg);
    setLine(pl(msg));
    setOut(true);
    if (hideTimer.current) clearTimeout(hideTimer.current);
    hideTimer.current = setTimeout(() => setOut(false), 4500);
  };

  // Switching tabs recoils him (so a line never lingers across tabs).
  useEffect(() => { setOut(false); }, [tab]);

  // ── The animation engine ── one rAF loop, all motion written to refs ──
  useEffect(() => {
    if (!enabled || sheetOpen) return;
    let raf = 0, prev = performance.now(), lastWork = 0;

    const frame = (now) => {
      raf = requestAnimationFrame(frame);
      const target = outRef.current ? 1 : 0;
      const moving = Math.abs(m.p - target) > 0.001;
      // Throttle to ~30fps while idle; the breathing/blink still update.
      if (!moving && now - lastWork < 33) return;
      const dt = Math.min(now - prev, 60); prev = now; lastWork = now;
      const t = now / 1000;

      // peek ⇄ out, eased toward the target (spring-ish)
      m.p += (target - m.p) * Math.min(1, dt / 130);
      const p = m.p;

      // subtle breathing (tiny vertical scale) — the silence, made to breathe
      const breath = 1 + Math.sin(t * 1.5) * 0.012;

      // occasional blink on a randomised cadence
      if (!m.nextBlink) m.nextBlink = now + 2500 + Math.random() * 3500;
      if (now > m.nextBlink && !m.blinkUntil) m.blinkUntil = now + 130;
      let eyeSy = 1;
      if (m.blinkUntil) {
        if (now > m.blinkUntil) { m.blinkUntil = 0; m.nextBlink = now + 2800 + Math.random() * 4200; }
        else { const bp = 1 - (m.blinkUntil - now) / 130; eyeSy = 1 - Math.sin(Math.PI * bp) * 0.9; }
      }

      // ── write transforms ──
      if (scaleRef.current) {
        const drop = (1 - p) * PEEK_DROP;            // dropped behind the bar line when idle
        const sc = 0.97 + 0.03 * p;                  // keep ~preview size; a subtle lift
        scaleRef.current.style.transform =
          "translateY(" + drop.toFixed(1) + "px) scale(" + sc.toFixed(3) + ")";
      }
      if (breathRef.current) breathRef.current.style.transform = "scaleY(" + breath.toFixed(4) + ")";
      if (eyeRef.current) eyeRef.current.setAttribute("transform",
        "matrix(1 0 0 " + eyeSy.toFixed(3) + " 0 " + (HEAD_EYE_CY * (1 - eyeSy)).toFixed(2) + ")");
      if (bubbleRef.current) {
        bubbleRef.current.style.opacity = p.toFixed(3);
        bubbleRef.current.style.transform = "scale(" + (0.92 + 0.08 * p).toFixed(3) + ")";
        bubbleRef.current.style.pointerEvents = p > 0.6 ? "auto" : "none";
      }
    };
    raf = requestAnimationFrame(frame);

    // Park entirely while the tab/app is hidden; resume cleanly when back.
    const onVis = () => {
      if (document.hidden) cancelAnimationFrame(raf);
      else { prev = performance.now(); lastWork = 0; raf = requestAnimationFrame(frame); }
    };
    document.addEventListener("visibilitychange", onVis);
    return () => { cancelAnimationFrame(raf); document.removeEventListener("visibilitychange", onVis); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, sheetOpen]);

  useEffect(() => () => { if (hideTimer.current) clearTimeout(hideTimer.current); }, []);

  if (!enabled || sheetOpen) return null;

  return (
    <div style={{
      position: "absolute", top: 0, left: 0, right: 0, bottom: bottomPx, zIndex: 40,
      pointerEvents: "none",
      overflow: "hidden",                 // ends on the bar line; clips Pip's base there
      WebkitUserSelect: "none", userSelect: "none",
      WebkitTouchCallout: "none", WebkitTapHighlightColor: "transparent",
    }}>
      {/* Corner anchor — sat on the tab-bar line (the overlay's bottom edge). Pip's
          base hides behind the line and he peeks up from the corner while idle; a
          tap lifts him fully into view, base still on the line — never floating.
          Holds the upward-opening bubble + head. */}
      <div style={{ position: "absolute", right: 12, bottom: 0 }}>
        {/* Speech bubble — stays mounted once Pip has spoken; the loop fades it in
            and out via opacity so the recoil reads smoothly. Opens up-left. */}
        {line && (
          <div ref={bubbleRef} style={{
            position: "absolute", bottom: "100%", right: 6, marginBottom: 12,
            width: "min(15rem, 68vw)", opacity: 0, transformOrigin: "right bottom",
            pointerEvents: "none",
          }}>
            <div style={{
              position: "relative",
              background: "var(--surface-dark)", color: "var(--on-dark)",
              borderRadius: 14, padding: "12px 32px 12px 14px", fontSize: 13.5, lineHeight: 1.45,
              fontFamily: "var(--sans)", boxShadow: "0 12px 30px rgba(0,0,0,0.32)",
              whiteSpace: "normal", overflowWrap: "break-word",
            }}>
              <span style={{
                position: "absolute", width: 12, height: 12, background: "var(--surface-dark)",
                transform: "rotate(45deg)", borderRadius: 2, bottom: -5, right: 22,
              }} aria-hidden="true"/>
              {line}
              <button onClick={() => setOut(false)} aria-label={tr("fechar")} style={{
                position: "absolute", top: 4, right: 4, width: 22, height: 22, borderRadius: "50%",
                border: "none", background: "transparent", color: "var(--on-dark-2)", cursor: "pointer",
                fontSize: 15, lineHeight: 1, display: "flex", alignItems: "center", justifyContent: "center",
              }}>×</button>
            </div>
          </div>
        )}

        {/* The head (tappable). The webkit resets kill the Android WebView
            tap-highlight / selection box that otherwise flashed on tap. */}
        <button onClick={speak} className="tap" aria-label={tr("papagaio")} style={{
          pointerEvents: "auto", border: "none", background: "transparent", padding: 0, cursor: "pointer",
          filter: "drop-shadow(0 5px 9px rgba(0,0,0,0.25))", lineHeight: 0, touchAction: "manipulation",
          WebkitTapHighlightColor: "transparent", WebkitTouchCallout: "none",
          WebkitUserSelect: "none", userSelect: "none", display: "block",
        }}>
          {/* scaleRef = peek⇄out (rises up from the tab-bar line);
              breathRef = the subtle, continuous breath. */}
          <div ref={scaleRef} style={{ willChange: "transform", transformOrigin: "bottom center" }}>
            <div ref={breathRef} style={{ willChange: "transform", transformOrigin: "bottom center" }}>
              <ParrotHead accent={accentColor} size={HEAD_SIZE} eyeRef={eyeRef} pupilRef={pupilRef}/>
            </div>
          </div>
        </button>
      </div>
    </div>
  );
}

Object.assign(window, { ParrotCompanion });
