const firstBootReloadKey = `nexel_first_boot_reload_${location.host}`;

if (sessionStorage.getItem(firstBootReloadKey) !== 'done') {
    sessionStorage.setItem(firstBootReloadKey, 'done');
    setTimeout(() => location.reload(), 1200);
} else {
startNexelSpeechPage();
}

function startNexelSpeechPage() {
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
const bubblesByIndex  = new Map();
let bubbleDisplayMs   = 8000;
let bubbleFadeMs      = 600;
const MAX_BUBBLES     = 3;
let lastTranscriptText  = '';
let lastTranscriptIndex = -1;
let lastTranscriptFinal = true;
let activeUtterance = null;
let mutedBeforeSpeech = false;

function stopBrowserSpeech(resumeRecognition = true) {
    if (!activeUtterance) return;
    if ('speechSynthesis' in window) window.speechSynthesis.cancel();
    activeUtterance = null;
    wsSend({ op: 'speech_state', d: { speaking: false } });
    isCurrentlyMuted = mutedBeforeSpeech;
    if (resumeRecognition && !isCurrentlyMuted && transcriber) {
        wsSend({ op: 'reset' });
        safeStart();
    }
}

function speakInBrowser(data) {
    const text = String(data.text || '').trim();
    if (!text || !('speechSynthesis' in window)) return;

    mutedBeforeSpeech = isCurrentlyMuted;
    isCurrentlyMuted = true;
    if (transcriber) {
        try { transcriber.stop(); } catch (_) {}
    }
    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = String(data.language || currentLang || 'en-US');
    if (data.muffled) {
        utterance.volume = 0.45;
        utterance.rate = 0.88;
        utterance.pitch = 0.82;
    }
    const languagePrefix = utterance.lang.toLowerCase().split('-')[0];
    const matchingVoice = window.speechSynthesis.getVoices().find((voice) =>
        String(voice.lang || '').toLowerCase().startsWith(languagePrefix)
    );
    if (matchingVoice) utterance.voice = matchingVoice;

    activeUtterance = utterance;
    utterance.onstart = () => {
        if (activeUtterance === utterance) wsSend({ op: 'speech_state', d: { speaking: true } });
    };
    utterance.onend = () => {
        if (activeUtterance === utterance) stopBrowserSpeech(true);
    };
    utterance.onerror = () => {
        if (activeUtterance === utterance) stopBrowserSpeech(true);
    };
    window.speechSynthesis.speak(utterance);
}

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
    const font = String(data.font || 'AUTO').toUpperCase();
    const maxWidth = Math.max(160, Number(data.maxWidth || 110) * 3);
    const padding = Math.max(4, Number(data.padding || 8));

    bubbleStack.style.setProperty('--bubble-fill', colorToHex(data.fillColor, '#ffffff'));
    bubbleStack.style.setProperty('--bubble-border', colorToHex(data.borderColor, '#000000'));
    bubbleStack.style.setProperty('--bubble-text', colorToHex(data.textColor, '#111111'));
    bubbleStack.style.setProperty('--bubble-max-width', `${maxWidth}px`);
    bubbleStack.style.setProperty('--bubble-padding-y', `${padding}px`);
    bubbleStack.style.setProperty('--bubble-padding-x', `${padding + 8}px`);
    bubbleStack.style.setProperty('--bubble-radius', style === 'SQUARED' ? '4px' : '30px');
    bubbleStack.style.setProperty('--bubble-font-family', fontFamilyForBubble(font));

    bubbleDisplayMs = Math.max(500, Number(data.displaySeconds || 8) * 1000);
    bubbleFadeMs = Math.max(1, Number(data.fadeSeconds || 0.6) * 1000);
    bubbleStack.style.setProperty('--bubble-fade-ms', `${bubbleFadeMs}ms`);
}

function fontFamilyForBubble(font) {
    switch (font) {
        case 'MINECRAFT':
            return '"Minecraft", "Minecraftia", "Unifont", "Courier New", monospace';
        case 'VANILLA_TWEAKS':
        case 'VANILLA_TWEAKS_ALT':
            return '"Trebuchet MS", "Segoe UI", "Unifont", sans-serif';
        case 'AUTO':
        case 'NEXEL':
        default:
            return '"Minecraft", "Minecraftia", "Unifont", "Courier New", monospace';
    }
}

function expireBubble(bubble) {
    if (!bubble || bubble.dataset.removing === 'true') return;
    bubble.dataset.removing = 'true';
    bubble.classList.add('hidden');
    setTimeout(() => bubble.remove(), bubbleFadeMs + 80);
}

function scheduleBubbleExpiration(bubble, index) {
    if (!bubble) return;
    if (bubble._nexelTimer) clearTimeout(bubble._nexelTimer);
    bubble._nexelTimer = setTimeout(() => {
        expireBubble(bubble);
        if (activeBubble === bubble) {
            activeBubble = null;
            activeBubbleIndex = -1;
            activeBubbleTimer = null;
        }
        if (bubblesByIndex.get(index) === bubble) {
            bubblesByIndex.delete(index);
        }
    }, bubbleDisplayMs + bubbleFadeMs);
    activeBubbleTimer = bubble._nexelTimer;
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

    const existingBubble = bubblesByIndex.get(index);
    if (existingBubble && existingBubble.dataset.removing === 'true') {
        bubblesByIndex.delete(index);
    }

    if (existingBubble && existingBubble.dataset.removing !== 'true') {
        activeBubble = existingBubble;
        activeBubbleIndex = index;
    } else if (!activeBubble || index !== activeBubbleIndex) {
        activeBubble = createBubble();
        activeBubbleIndex = index;
        bubblesByIndex.set(index, activeBubble);
    }

    const textElement = activeBubble.querySelector('.bubble-text');
    if (textElement) textElement.innerText = normalized;

    scheduleBubbleExpiration(activeBubble, index);

    // Keep the finalized bubble indexed so Edge punctuation/capitalization
    // corrections update in place instead of replaying the entrance animation.
}

function flushPendingTranscriptAsFinal() {
    const text = String(lastTranscriptText || '').trim();
    if (!text || lastTranscriptFinal || lastTranscriptIndex < 0) return;

    wsSend({
        op: 'transcript',
        d: {
            text,
            final: true,
            results: [{ text, confidence: 1 }],
            index: lastTranscriptIndex
        }
    });

    updateBubbleText(text, lastTranscriptIndex, true);
    lastTranscriptFinal = true;
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

        flushPendingTranscriptAsFinal();

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
        if (primaryText) {
            lastTranscriptText = primaryText;
            lastTranscriptIndex = ev.resultIndex;
            lastTranscriptFinal = !!result.isFinal;
        }

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
                if (activeUtterance) {
                    mutedBeforeSpeech = isMuted;
                    break;
                }
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
                speakInBrowser(data.d || {});
                break;
            }

            case 'stop_speech': {
                stopBrowserSpeech(true);
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
}
