let ws = new WebSocket('ws://127.0.0.1:%SOCKET_PORT%');

window.SpeechRecognition = window.webkitSpeechRecognition || window.SpeechRecognition;

const pause = document.getElementById('pause');
const bubbleStack = document.getElementById('bubble_stack');

/**
 * @type {SpeechRecognition}
 */
let transcriber;

let lastReset        = 0;
let totalFastResets  = 0;   // era "totalResetsBelow50ms" mas a logica estava invertida
let isErrored        = false;
let wasNoSpeech      = false;
let isCurrentlyMuted = false;
let isStarting       = false;   // guard contra duplo .start() no Edge
let currentLang      = null;
let activeBubble      = null;
let activeBubbleIndex = -1;
let activeBubbleTimer = null;
let bubbleDisplayMs   = 8000;
let bubbleFadeMs      = 600;
const MAX_BUBBLES     = 3;

// ─── WebSocket helpers ────────────────────────────────────────────────────────

function wsSend(obj) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(obj));
    }
}

function colorToHex(color, fallback) {
    if (typeof color !== 'number' || Number.isNaN(color)) return fallback;
    return `#${(color & 0xFFFFFF).toString(16).padStart(6, '0')}`;
}

function applyBubbleAppearance(data) {
    if (!bubbleStack) return;

    const style = String(data.style || 'ROUNDED').toUpperCase();
    const maxWidth = Math.max(160, Number(data.maxWidth || 110) * 3);
    const padding = Math.max(4, Number(data.padding || 8));

    bubbleStack.style.setProperty('--bubble-fill', colorToHex(data.fillColor, '#ffffff'));
    bubbleStack.style.setProperty('--bubble-border', colorToHex(data.borderColor, '#000000'));
    bubbleStack.style.setProperty('--bubble-text', colorToHex(data.textColor, '#111111'));
    bubbleStack.style.setProperty('--bubble-max-width', `${maxWidth}px`);
    bubbleStack.style.setProperty('--bubble-padding-y', `${padding}px`);
    bubbleStack.style.setProperty('--bubble-padding-x', `${padding + 8}px`);
    bubbleStack.style.setProperty('--bubble-radius', style === 'SQUARED' ? '4px' : '30px');

    bubbleDisplayMs = Math.max(500, Number(data.displaySeconds || 8) * 1000);
    bubbleFadeMs = Math.max(1, Number(data.fadeSeconds || 0.6) * 1000);
    bubbleStack.style.setProperty('--bubble-fade-ms', `${bubbleFadeMs}ms`);
}

function expireBubble(bubble) {
    if (!bubble || bubble.dataset.removing === 'true') return;
    bubble.dataset.removing = 'true';
    bubble.classList.add('hidden');
    setTimeout(() => bubble.remove(), bubbleFadeMs + 80);
}

function scheduleActiveBubbleExpiration() {
    if (activeBubbleTimer) clearTimeout(activeBubbleTimer);
    activeBubbleTimer = setTimeout(() => {
        expireBubble(activeBubble);
        activeBubble = null;
        activeBubbleIndex = -1;
        activeBubbleTimer = null;
    }, bubbleDisplayMs + bubbleFadeMs);
}

function trimBubbleStack() {
    if (!bubbleStack) return;
    const bubbles = Array.from(bubbleStack.querySelectorAll('.speech-bubble'));
    for (const bubble of bubbles.slice(MAX_BUBBLES)) {
        expireBubble(bubble);
    }
}

function createBubble() {
    const bubble = document.createElement('div');
    bubble.className = 'speech-bubble hidden';

    const text = document.createElement('p');
    text.className = 'bubble-text';
    bubble.appendChild(text);
    bubbleStack.prepend(bubble);

    requestAnimationFrame(() => bubble.classList.remove('hidden'));
    trimBubbleStack();
    return bubble;
}

function updateBubbleText(text, index, isFinal) {
    const normalized = String(text || '').replace(/\s+/g, ' ').trim();
    if (!normalized || !bubbleStack) return;

    if (!activeBubble || index !== activeBubbleIndex) {
        activeBubble = createBubble();
        activeBubbleIndex = index;
    }

    const textElement = activeBubble.querySelector('.bubble-text');
    if (textElement) textElement.innerText = normalized;

    scheduleActiveBubbleExpiration();

    if (isFinal) {
        activeBubble = null;
        activeBubbleIndex = -1;
    }
}

function reconnectWS() {
    console.warn('WebSocket fechou — reconectando em 2s…');
    setTimeout(() => {
        ws = new WebSocket('ws://127.0.0.1:%SOCKET_PORT%');
        attachWSHandlers();
    }, 2000);
}

// ─── Transcriber ──────────────────────────────────────────────────────────────

function safeStart() {
    if (isStarting || isCurrentlyMuted || isErrored) return;
    try {
        isStarting = true;
        transcriber.start();
        // Edge demora ~200 ms para confirmar que esta rodando
        setTimeout(() => { isStarting = false; }, 300);
    } catch (e) {
        isStarting = false;
        console.warn('safeStart falhou:', e.message);
        // Se ja estava rodando nao tem problema, ignora
        if (e.name !== 'InvalidStateError') {
            scheduleRestart();
        }
    }
}

function scheduleRestart(delay = 600) {
    isStarting = false;
    if (isErrored || isCurrentlyMuted) return;
    setTimeout(() => {
        wsSend({ op: 'reset' });
        safeStart();
    }, delay);
}

function setupTranscriber(lang) {
    console.log(`Iniciando transcriber — lang: ${lang}`);

    // Para o anterior sem disparar reinicio automatico
    isCurrentlyMuted = true;
    if (transcriber) {
        try { transcriber.stop(); } catch (_) {}
    }
    isCurrentlyMuted = false;
    isStarting       = false;
    isErrored        = false;
    wasNoSpeech      = false;
    totalFastResets  = 0;
    lastReset        = Date.now();
    currentLang      = lang;

    transcriber = new SpeechRecognition();
    transcriber.lang             = lang;
    transcriber.continuous       = true;
    transcriber.interimResults   = true;
    transcriber.maxAlternatives  = 3;

    // ── onerror ──────────────────────────────────────────────────────────────
    transcriber.onerror = (ev) => {
        console.error('SpeechRecognition error:', ev.error);

        switch (ev.error) {
            case 'no-speech':
                // Normal — o onend vai reiniciar
                wasNoSpeech = true;
                break;

            case 'audio-capture':
            case 'not-allowed':
                // Permissao negada ou mic ocupado — para tudo
                isErrored = true;
                pause.classList.add('visible');
                wsSend({ op: 'error', d: { type: ev.error } });
                break;

            case 'network':
                // Erro de rede do STT — tenta reiniciar depois de um tempo
                scheduleRestart(1500);
                break;

            case 'aborted':
                // Edge aborta internamente as vezes; onend cuida do restart
                break;

            default:
                // Para erros desconhecidos, tenta recuperar
                scheduleRestart(800);
        }
    };

    // ── onend ────────────────────────────────────────────────────────────────
    transcriber.onend = () => {
        isStarting = false;

        if (isErrored || isCurrentlyMuted) return;

        const elapsed = Date.now() - lastReset;
        lastReset = Date.now();

        if (wasNoSpeech) {
            // Silencio detectado — reinicia normalmente, sem penalidade
            wasNoSpeech = false;
            wsSend({ op: 'reset' });
            safeStart();
            return;
        }

        // CORRECAO: resets RAPIDOS (< 50 ms) sao problematicos, nao lentos
        if (elapsed < 50) {
            totalFastResets++;
            console.warn(`Reset rapido #${totalFastResets} (${elapsed}ms)`);

            if (totalFastResets >= 30) {
                isErrored = true;
                pause.classList.add('visible');
                wsSend({ op: 'error', d: { type: 'too_many_resets' } });
                return;
            }

            // Espera um pouco antes de reiniciar para nao entrar em loop
            scheduleRestart(300 + totalFastResets * 20);
            return;
        }

        // Reset saudavel — zera o contador
        totalFastResets = 0;

        console.log(`Transcriber reiniciando (apos ${elapsed}ms)…`);
        wsSend({ op: 'reset' });
        safeStart();
    };

    // ── onresult ─────────────────────────────────────────────────────────────
    transcriber.onresult = (ev) => {
        // Cada resultado pode ter alternativas; enviamos todas
        const result = ev.results.item(ev.resultIndex);
        const items = result;
        const results = [];

        for (let j = 0; j < items.length; j++) {
            const d = items.item(j);
            results.push({ text: d.transcript, confidence: d.confidence });
        }

        const primaryText = results.length > 0 ? String(results[0].text || '').trim() : '';

        wsSend({
            op: 'transcript',
            d: {
                text: primaryText,
                final: !!result.isFinal,
                results,
                index: ev.resultIndex
            }
        });

        updateBubbleText(primaryText, ev.resultIndex, !!result.isFinal);
    };

    wsSend({ op: 'reset' });
    safeStart();
}

// ─── WebSocket handlers ───────────────────────────────────────────────────────

function attachWSHandlers() {
    ws.onopen = () => {
        console.log('WebSocket conectado');
        // Se havia uma lang configurada antes da reconexao, restaura
        if (currentLang && !isErrored) {
            setupTranscriber(currentLang);
        }
    };

    ws.onmessage = (ev) => {
        const data = JSON.parse(ev.data);

        switch (data.op) {
            case 'set_language': {
                const lang = data.d.language;

                if (!SpeechRecognition) {
                    wsSend({ op: 'error', d: { type: 'no_support' } });
                    pause.classList.add('visible');
                    return;
                }

                setupTranscriber(lang);
                break;
            }

            case 'set_muted': {
                const isMuted = data.d.muted;
                isCurrentlyMuted = isMuted;

                if (!isMuted && transcriber) {
                    wsSend({ op: 'reset' });
                    safeStart();
                } else if (transcriber) {
                    try { transcriber.stop(); } catch (_) {}
                }
                break;
            }

            case 'set_bubble_appearance': {
                applyBubbleAppearance(data.d || {});
                break;
            }

            case 'speak': {
                // TTS agora sai pelo mod usando a saida padrao do Windows.
                break;
            }

            case 'stop_speech': {
                break;
            }
        }
    };

    ws.onerror = (e) => {
        console.error('WebSocket erro:', e);
    };

    ws.onclose = () => {
        if (transcriber) {
            // Para o transcriber mas NAO marca isErrored — o WS pode voltar
            isCurrentlyMuted = true;
            try { transcriber.stop(); } catch (_) {}
        }
        reconnectWS();
    };
}

attachWSHandlers();
